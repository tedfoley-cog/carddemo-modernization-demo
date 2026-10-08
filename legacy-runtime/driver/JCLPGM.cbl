       IDENTIFICATION DIVISION.
       PROGRAM-ID. JCLPGM.
      * Stand-in for z/OS EXEC PGM=xxx,PARM='...'. Calls the program with
      * the standard MVS PARM area (halfword length + text) and passes the
      * program RETURN-CODE back as the step condition code.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 WS-PGM            PIC X(8).
       01 WS-PARM-TEXT      PIC X(100).
       01 MVS-PARM.
          05 MVS-PARM-LEN   PIC S9(4) COMP.
          05 MVS-PARM-DATA  PIC X(100).
       PROCEDURE DIVISION.
           ACCEPT WS-PGM FROM ENVIRONMENT 'JCL_PGM'
           ACCEPT WS-PARM-TEXT FROM ENVIRONMENT 'JCL_PARM'
           MOVE WS-PARM-TEXT TO MVS-PARM-DATA
           MOVE 0 TO MVS-PARM-LEN
           INSPECT FUNCTION REVERSE(WS-PARM-TEXT)
              TALLYING MVS-PARM-LEN FOR LEADING SPACES
           COMPUTE MVS-PARM-LEN = 100 - MVS-PARM-LEN
           CALL WS-PGM USING MVS-PARM
           STOP RUN RETURNING RETURN-CODE.
