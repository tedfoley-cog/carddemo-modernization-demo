# Reverse engineering (mftb COBOL toolkit)

Store produced by the mftb plugin over `../app`. Rerun from this directory with `PYTHONPATH=<mftb plugin root>`:

```bash
python3 -m mftb scan-estate ../app             # store/catalog.json
python3 -m mftb modules --check                # store/modules.json partitions the catalog
python3 -m mftb scan --module CARDDEMO --from-catalog
python3 -m mftb scan-chains --module CARDDEMO --store store CARDDEMO MONTHLY-InterestCalculation
python3 -m mftb scan-online --module CARDDEMO --store store --transaction CC00 --transaction CAVW --transaction CB00
python3 -m mftb report-estate                  # store/reports/estate.html
```

Agent-written judgment (cited `path:line`): `store/modules.json`, `store/*/descriptions.json`,
`store/CARDDEMO/chainnotes.json`, `store/CARDDEMO/onlinenotes.json`. `fill_descriptions.py` seeds one-line
descriptions from each program's own `Function:` header.
