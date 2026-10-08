/*
 * LD_PRELOAD shim used for golden-file runs. GnuCOBOL takes the date/time of
 * FUNCTION CURRENT-DATE from COB_CURRENT_DATE but the hundredths of a second
 * from the wall clock; zeroing the sub-second part makes every run of the
 * legacy stream byte-for-byte reproducible.
 */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <time.h>

int clock_gettime(clockid_t id, struct timespec *ts) {
    static int (*real)(clockid_t, struct timespec *);
    if (!real) real = (int (*)(clockid_t, struct timespec *))dlsym(RTLD_NEXT, "clock_gettime");
    int rc = real(id, ts);
    if (rc == 0 && id == CLOCK_REALTIME) ts->tv_nsec = 0;
    return rc;
}
