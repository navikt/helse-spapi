
```mermaid
---
title: Overordnet systemarkitekturskisse
---
flowchart LR
    spapi --> spøkelse
    spøkelse -->|lagrer fortløpende fra kafka| kafka[/tbd.v1/]
    spøkelse -->|henter ved behov fra infotrygd?| infotrygd[(infotrygd-replika)]

```

