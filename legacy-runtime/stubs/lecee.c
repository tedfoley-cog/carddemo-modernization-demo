/*
 * Linux stand-ins for the z/OS Language Environment services and the one
 * assembler module (COBDATFT) that CardDemo calls. Behaviour follows the
 * public IBM LE documentation for CEE3ABD / CEEDAYS and app/asm/COBDATFT.asm.
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>

static int be32(const unsigned char *p) { return (int)((p[0] << 24) | (p[1] << 16) | (p[2] << 8) | p[3]); }
static void put_be32(unsigned char *p, int v) { p[0] = v >> 24; p[1] = v >> 16; p[2] = v >> 8; p[3] = v; }
static int be16(const unsigned char *p) { return (short)((p[0] << 8) | p[1]); }

/* CALL 'CEE3ABD' USING ABCODE TIMING  - terminate with user abend */
int CEE3ABD(unsigned char *abcode, unsigned char *timing) {
    int code = abcode ? be32(abcode) : 0;
    fflush(stdout);
    fprintf(stderr, "CEE3ABD: USER ABEND U%04d\n", code);
    printf("CEE3ABD: USER ABEND U%04d\n", code);
    fflush(stdout);
    exit(code ? (code & 0xff ? code & 0xff : 16) : 16);
    (void)timing;
}

/* Feedback tokens as coded in the 88-levels of app/cbl/CSUTLDTC.cbl */
static void fc(unsigned char *f, int msgno) {
    memset(f, 0, 12);
    if (!msgno) return;
    f[0] = 0; f[1] = 3;                      /* severity 3 */
    f[2] = msgno >> 8; f[3] = msgno & 0xff;   /* message number */
    f[4] = 0x59; f[5] = 0xC3; f[6] = 0xC5; f[7] = 0xC5;  /* case/facility 'CEE' */
}

static int is_leap(int y) { return (y % 4 == 0 && y % 100 != 0) || y % 400 == 0; }
static long days_from_civil(int y, int m, int d) { /* days since 1970-01-01 */
    y -= m <= 2; long era = (y >= 0 ? y : y - 399) / 400; unsigned yoe = (unsigned)(y - era * 400);
    unsigned doy = (153 * (m + (m > 2 ? -3 : 9)) + 2) / 5 + d - 1; unsigned doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
    return era * 146097 + (long)doe - 719468;
}

/* CALL 'CEEDAYS' USING date-vstring picture-vstring lillian feedback */
int CEEDAYS(unsigned char *date, unsigned char *pic, unsigned char *lil, unsigned char *feedback) {
    int dl = be16(date), pl = be16(pic);
    const char *ds = (const char *)date + 2, *ps = (const char *)pic + 2;
    int y = -1, m = -1, d = -1, i;
    put_be32(lil, 0);
    while (pl > 0 && ps[pl - 1] == ' ') pl--;
    if (pl <= 0) { fc(feedback, 0x09D6); return 0; }
    if (dl < pl) { fc(feedback, 0x09CB); return 0; }
    for (i = 0; i < pl;) {
        int n = 0; int *t = NULL;
        if (!strncmp(ps + i, "YYYY", 4)) { t = &y; n = 4; }
        else if (!strncmp(ps + i, "MM", 2)) { t = &m; n = 2; }
        else if (!strncmp(ps + i, "DD", 2)) { t = &d; n = 2; }
        if (t) {
            int v = 0, k;
            for (k = 0; k < n; k++) { if (!isdigit((unsigned char)ds[i + k])) { fc(feedback, 0x09D8); return 0; } v = v * 10 + ds[i + k] - '0'; }
            *t = v; i += n;
        } else {
            if (isalpha((unsigned char)ps[i])) { fc(feedback, 0x09D6); return 0; }
            i++;
        }
    }
    if (y < 0 || m < 0 || d < 0) { fc(feedback, 0x09CB); return 0; }
    if (y == 0) { fc(feedback, 0x09D9); return 0; }
    if (m < 1 || m > 12) { fc(feedback, 0x09D5); return 0; }
    { static const int mdays[] = {31,28,31,30,31,30,31,31,30,31,30,31};
      int md = mdays[m - 1] + (m == 2 && is_leap(y));
      if (d < 1 || d > md) { fc(feedback, 0x09CC); return 0; } }
    if (y < 1582 || (y == 1582 && (m < 10 || (m == 10 && d < 15))) || y > 9999) { fc(feedback, 0x09D1); return 0; }
    put_be32(lil, (int)(days_from_civil(y, m, d) - days_from_civil(1582, 10, 14)));
    fc(feedback, 0);
    return 0;
}

/* CALL 'COBDATFT' USING CODATECN-REC  (port of app/asm/COBDATFT.asm) */
int COBDATFT(unsigned char *r) {
    unsigned char *intype = r, *inp = r + 1, *outtype = r + 21, *out = r + 22, *err = r + 42;
    if (*intype == '1') {
        if (inp[4] == '-' || *outtype == '2') goto bad;
        memcpy(out, inp, 4); out[4] = '-'; memcpy(out + 5, inp + 4, 2); out[7] = '-'; memcpy(out + 8, inp + 6, 2);
        return 0;
    }
    if (*intype == '2') {
        if (*outtype == '1') goto bad;
        memcpy(out, inp, 4); memcpy(out + 4, inp + 5, 2); memcpy(out + 6, inp + 8, 2);
        return 0;
    }
bad:
    memcpy(err, "INVALID INPUT", 13);
    return 0;
}
