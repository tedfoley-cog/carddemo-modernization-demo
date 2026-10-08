/*
 * cicshost - Linux CICS task host for translated CardDemo programs.
 *
 * One process per terminal session, driven by region.py over fd 3 (in) / fd 4 (out);
 * COBOL DISPLAY output stays on stdout. Region sends a task:
 *     TASK <PROGRAM>\nEIB <hex>\nCOMMAREA <hex>\nGO\n
 * the host runs the program with a fresh WORKING-STORAGE (as CICS does for each task) and
 * answers DONE. Every EXEC CICS arrives here as CALL 'CICSCMD' (see translate.py):
 *   - file control (READ/WRITE/REWRITE/DELETE/STARTBR/READNEXT/READPREV/ENDBR/UNLOCK)
 *     is executed locally through the generated FH<cluster> modules (gen_fh.py);
 *   - everything else (BMS, RETURN, XCTL, ASSIGN, ASKTIME, ...) is forwarded to the region:
 *     CMD <spec>\nEIB <hex>\nARG <i> <size> <hex> | NUM <i> <value>\n...GO\n
 *     and the region replies EIB/ARG/NUM updates followed by OK.
 */
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include <libcob.h>

#define EIB_LEN 85
#define MAXARGS 24
#define LINE_MAX_ 200000

static FILE *rin, *rout;
static unsigned char eib[EIB_LEN];
static unsigned char commarea[32768];
static char line[LINE_MAX_];

struct fct { char name[9]; char prog[32]; char path; int opened; };
static struct fct fct[32];
static int nfct;

static void put_hex(const unsigned char *p, int n) {
    static const char *h = "0123456789ABCDEF";
    for (int i = 0; i < n; i++) { fputc(h[p[i] >> 4], rout); fputc(h[p[i] & 15], rout); }
}
static int get_hex(const char *s, unsigned char *p, int max) {
    int n = 0;
    while (isxdigit((unsigned char)s[0]) && isxdigit((unsigned char)s[1]) && n < max) {
        unsigned int v; sscanf(s, "%2x", &v); p[n++] = (unsigned char)v; s += 2;
    }
    return n;
}
static void read_line(void) {
    if (!fgets(line, sizeof line, rin)) exit(0);
    line[strcspn(line, "\n")] = 0;
}
static void set_be32(unsigned char *p, int v) {
    p[0] = (v >> 24) & 255; p[1] = (v >> 16) & 255; p[2] = (v >> 8) & 255; p[3] = v & 255;
}

/* ---- command spec: "READ DATASET=1 INTO=2 RIDFLD=3 GTEQ RESP=4" ---- */
static char spec[512];
static char *toks[64];
static int ntok;
/* Parameters are captured at entry: a nested cob_call (file handlers) resets libcob's
   current-call parameter state, after which cob_get_param_field would see the callee's. */
static cob_field *pcache[64];
static int pcount;
static void capture_params(void) {
    pcount = cob_get_num_params();
    if (pcount > 63) pcount = 63;
    for (int i = 1; i <= pcount; i++) pcache[i] = cob_get_param_field(i, "CICSCMD");
}
static cob_field *param(int i) { return (i >= 1 && i <= pcount) ? pcache[i] : NULL; }
static void parse_spec(void) {
    cob_field *f = param(2);
    int n = f->size < sizeof spec - 1 ? (int)f->size : (int)sizeof spec - 1;
    memcpy(spec, f->data, n); spec[n] = 0;
    char tmp[512]; strcpy(tmp, spec);
    static char store[512]; strcpy(store, tmp);
    ntok = 0;
    for (char *t = strtok(store, " "); t && ntok < 64; t = strtok(NULL, " ")) toks[ntok++] = t;
}
static int opt_index(const char *name) {           /* parameter number of NAME=n, or 0 */
    size_t l = strlen(name);
    for (int i = 1; i < ntok; i++)
        if (!strncmp(toks[i], name, l) && toks[i][l] == '=') return atoi(toks[i] + l + 1) + 2;
    return 0;
}
static cob_field *opt(const char *name) { int i = opt_index(name); return i ? param(i) : NULL; }
static int flag(const char *name) {
    for (int i = 1; i < ntok; i++) if (!strcmp(toks[i], name)) return 1;
    return 0;
}
static void field_str(cob_field *f, char *out, int max) {
    int n = (int)f->size < max - 1 ? (int)f->size : max - 1;
    memcpy(out, f->data, n); out[n] = 0;
    while (n > 0 && (out[n - 1] == ' ' || out[n - 1] == 0)) out[--n] = 0;
}

static void set_resp(int resp, int resp2) {
    set_be32(eib + 76, resp); set_be32(eib + 80, resp2);
    cob_field *r = opt("RESP"), *r2 = opt("RESP2");
    if (r) cob_set_int(r, resp);
    if (r2) cob_set_int(r2, resp2);
    if (resp && !r) {                 /* unhandled condition: CICS abends the task */
        fprintf(rout, "ABEND ARSP %d %s\n", resp, spec); fflush(rout);
        fflush(stdout); exit(3);
    }
}

/* ---- file control ---- */
static void load_fct(void) {
    const char *p = getenv("CICS_FCT");
    FILE *f = fopen(p ? p : "fct.txt", "r");
    if (!f) { perror("CICS_FCT"); exit(2); }
    while (nfct < 32 && fscanf(f, "%8s %31s %c", fct[nfct].name, fct[nfct].prog, &fct[nfct].path) == 3) nfct++;
    fclose(f);
}
static int status_to_resp(const char *st, const char *verb) {
    if (st[0] == '0') return 0;
    if (!strncmp(st, "23", 2)) return 13;                       /* NOTFND */
    if (!strncmp(st, "22", 2)) return 14;                       /* DUPREC */
    if (!strncmp(st, "10", 2) || !strncmp(st, "46", 2)) return 20; /* ENDFILE */
    if (!strncmp(st, "35", 2)) return 19;                       /* NOTOPEN */
    (void)verb;
    return 17;                                                  /* IOERR */
}
static int file_cmd(const char *verb) {
    cob_field *ds = opt("DATASET"); if (!ds) ds = opt("FILE");
    char name[16]; field_str(ds, name, sizeof name);
    struct fct *e = NULL;
    for (int i = 0; i < nfct; i++) if (!strcmp(fct[i].name, name)) e = &fct[i];
    if (!e) { set_resp(12, 0); return 0; }                      /* FILENOTFOUND */
    if (!strcmp(verb, "UNLOCK")) { set_resp(0, 0); return 0; }

    char func[9] = "        ", path = e->path, key[32], st[3] = "00";
    static unsigned char rec[32768];
    const char *fn = verb;
    if (!strcmp(verb, "READ") && flag("GTEQ")) fn = "READGE";
    if (!strcmp(verb, "STARTBR") && flag("EQUAL")) fn = "STARTEQ";
    memcpy(func, fn, strlen(fn));
    memset(key, ' ', sizeof key);
    cob_field *rid = opt("RIDFLD");
    if (rid) memcpy(key, rid->data, rid->size < 32 ? rid->size : 32);
    memset(rec, ' ', sizeof rec);
    cob_field *from = opt("FROM");
    if (from) memcpy(rec, from->data, from->size);
    void *argv[5] = {func, &path, key, rec, st};
    cob_call(e->prog, 5, argv);
    e->opened = 1;
    int resp = status_to_resp(st, verb);
    if (!resp && (!strncmp(verb, "READ", 4))) {
        cob_field *into = opt("INTO");
        if (into) memcpy(into->data, rec, into->size);
        if (rid) memcpy(rid->data, key, rid->size < 32 ? rid->size : 32);
    }
    set_resp(resp, 0);
    return 0;
}

/* ---- forwarded commands ---- */
static int forward(void) {
    int n = pcount;
    fprintf(rout, "CMD %s\nEIB ", spec); put_hex(eib, EIB_LEN); fputc('\n', rout);
    for (int i = 3; i <= n; i++) {
        cob_field *f = param(i);
        if (!f) continue;
        if (COB_FIELD_IS_NUMERIC(f)) fprintf(rout, "NUM %d %lld\n", i - 2, (long long)cob_get_llint(f));
        else { fprintf(rout, "ARG %d %d ", i - 2, (int)f->size); put_hex(f->data, (int)f->size); fputc('\n', rout); }
    }
    fputs("GO\n", rout); fflush(rout);
    for (;;) {
        read_line();
        if (!strcmp(line, "OK")) break;
        if (!strncmp(line, "EIB ", 4)) get_hex(line + 4, eib, EIB_LEN);
        else if (!strncmp(line, "ARG ", 4) || !strncmp(line, "NUM ", 4)) {
            char *sp; int i = (int)strtol(line + 4, &sp, 10); cob_field *f = param(i + 2);
            if (!f) continue;
            if (line[0] == 'N') cob_put_s64_param(i + 2, (cob_s64_t)atoll(sp + 1));  /* no nested cob_call here */
            else { unsigned char buf[32768]; int k = get_hex(sp + 1, buf, sizeof buf);
                   memcpy(f->data, buf, (size_t)k < f->size ? (size_t)k : f->size); }
        } else if (!strncmp(line, "KILL", 4)) { fflush(stdout); exit(3); }
    }
    return 0;
}

int CICSCMD(void *a0, void *a1, void *a2, void *a3, void *a4, void *a5, void *a6, void *a7,
            void *a8, void *a9, void *a10, void *a11, void *a12, void *a13, void *a14,
            void *a15, void *a16, void *a17, void *a18, void *a19, void *a20) {
    capture_params();
    (void)a0;(void)a1;(void)a2;(void)a3;(void)a4;(void)a5;(void)a6;(void)a7;(void)a8;(void)a9;
    (void)a10;(void)a11;(void)a12;(void)a13;(void)a14;(void)a15;(void)a16;(void)a17;(void)a18;
    (void)a19;(void)a20;
    parse_spec();
    const char *v = toks[0];
    static const char *filev[] = {"READ", "WRITE", "REWRITE", "DELETE", "STARTBR", "READNEXT",
                                  "READPREV", "ENDBR", "UNLOCK", NULL};
    for (int i = 0; filev[i]; i++) if (!strcmp(v, filev[i])) return file_cmd(v);
    return forward();
}

int main(int argc, char **argv) {
    cob_init(argc, argv);
    rin = fdopen(3, "r"); rout = fdopen(4, "w");
    if (!rin || !rout) { fprintf(stderr, "cicshost: fd 3/4 not connected\n"); return 2; }
    load_fct();
    for (;;) {
        char prog[32] = "";
        memset(commarea, 0, sizeof commarea);
        for (;;) {
            read_line();
            if (!strncmp(line, "TASK ", 5)) { strncpy(prog, line + 5, 31); }
            else if (!strncmp(line, "EIB ", 4)) get_hex(line + 4, eib, EIB_LEN);
            else if (!strncmp(line, "COMMAREA ", 9)) get_hex(line + 9, commarea, sizeof commarea);
            else if (!strcmp(line, "GO")) break;
        }
        void *args[2] = {eib, commarea};
        cob_call(prog, 2, args);
        for (int i = 0; i < nfct; i++) if (fct[i].opened) {
            char func[9] = "CLOSE   ", path = 'P', key[32], st[3];
            static unsigned char rec[32768];
            void *a[5] = {func, &path, key, rec, st};
            cob_call(fct[i].prog, 5, a);
            fct[i].opened = 0;
        }
        cob_cancel(prog);
        fflush(stdout);
        fputs("DONE\n", rout); fflush(rout);
    }
}
