SPAPI eksponerer sykepengeperioder til eksterne konsumenter.

I første omgang er dette kun Fellesordningen for AFP.

---
Overordnet systemarkitekturskisse
---

```mermaid
---
title: konsumenter henter data
---
flowchart LR
    spapi -->|https| spøkelse
    spøkelse -->|spleis-data| kafka[(spøkelse-db)]
    spøkelse -->|infotrygd-data| sykepengeperiode-api --> infotrygd[(infotrygd-replika)]
```

```mermaid
---
title: spøkelse cacher spleis-data
---
flowchart LR
    spøkelse -->|leser fra kafka| kafka[/tbd.utbetaling/] --> database[(spøkelse-db)]
```

```mermaid
---
title: Use Case AFP henter data 
---
flowchart TB
    afp -->|Gi meg data for fnr og uttaksdato| spapi
    spapi -->|Gi meg data for fnr og uttaksdato| spøkelse
    spøkelse -->|Vær så god| spapi 
    spapi -->|Her er data vi skal gi til AFP| sporingslogg
    sporingslogg -->|Den er god| spapi 
    spapi -->|Vær så god| afp
```

```mermaid
---
title: Use Case AFP henter data, denne gangen som en sekvens med avgjørelser
---
sequenceDiagram
    AFP ->> SPAPI: Gi meg data for FNR og UTTAKSDATO
    activate SPAPI
    SPAPI ->> Spøkelse: Gi meg data for FNR og UTTAKSDATO
    activate Spøkelse
    Spøkelse ->> SpøkelseDB: Gi meg spleis-data for FNR og UTTAKSDATO
    Spøkelse ->> InfotrygdReplikering: Gi meg infotrygd-data for FNR og UTTAKSDATO
    Spøkelse ->> Spøkelse: Slå sammen data
    Spøkelse -->> SPAPI: Her er dataen
    deactivate Spøkelse
    SPAPI ->> Sporingslogg: Dette er data vi ønkser å gi til FO AFP
    alt logging virker
        Sporingslogg -->> SPAPI: Logging er ok
        SPAPI -->> AFP: Her er dataen du ville ha
    else logging feilet
        Sporingslogg -->> SPAPI: Klarte ikke logge
        SPAPI -->> AFP: Her er en feilmelding
    end
    deactivate SPAPI
```



# API-definisjon

I påvente av at vi lager en swagger-greie eller tilsvarende

Request:
POST
parametere er fnr (11 siffer) og dato

Response: 
JSON-objekt som inneholder et resultat som er en liste med fom (dato), tom(dato) og grad (heltall)

Alle datoer er på format `yyyy-mm-dd`