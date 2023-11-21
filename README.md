Spaπ eksponerer sykepengeperioder til eksterne konsumenter.

I første omgang er dette kun Fellesordningen for AFP.

---
Overordnet systemarkitekturskisse
---

```mermaid
---
title: Konsumenter henter data
---
flowchart LR
    Spaπ -->|https| spøkelse
    spøkelse -->|spleis-data| kafka[(spøkelse-db)]
    spøkelse -->|infotrygd-data| sykepengeperiode-api --> infotrygd[(infotrygd-replika)]
```

```mermaid
---
title: Spøkelse cacher spleis-data
---
flowchart LR
    spøkelse -->|leser fra kafka| kafka[/tbd.utbetaling/] --> database[(spøkelse-db)]
```

```mermaid
---
title: Use Case FO AFP henter data 
---
flowchart TB
    fo-afp -->|Gi meg data for fnr og uttaksdato| Spaπ
    Spaπ -->|Gi meg data for fnr og uttaksdato| spøkelse
    spøkelse -->|Vær så god| Spaπ 
    Spaπ -->|Her er data vi skal gi til AFP| sporingslogg
    sporingslogg -->|Den er god| Spaπ 
    Spaπ -->|Vær så god| fo-afp
```

```mermaid
---
title: Use Case AFP henter data, denne gangen som en sekvens med avgjørelser
---
sequenceDiagram
    FO-AFP ->> Spaπ: Gi meg data for FNR og UTTAKSDATO
    activate Spaπ
    Spaπ ->> Spøkelse: Gi meg data for FNR og UTTAKSDATO
    activate Spøkelse
    Spøkelse ->> SpøkelseDB: Gi meg spleis-data for FNR og UTTAKSDATO
    Spøkelse ->> InfotrygdReplikering: Gi meg infotrygd-data for FNR og UTTAKSDATO
    Spøkelse ->> Spøkelse: Slå sammen data
    Spøkelse -->> Spaπ: Her er dataen
    deactivate Spøkelse
    Spaπ ->> Sporingslogg: Dette er data vi ønkser å gi til FO AFP
    alt logging virker
        Sporingslogg -->> Spaπ: Logging er ok
        Spaπ -->> FO-AFP: Her er dataen du ville ha
    else logging feilet
        Sporingslogg -->> Spaπ: Klarte ikke logge
        Spaπ -->> FO-AFP: Her er en feilmelding
    end
    deactivate Spaπ
```



# API-definisjon

I påvente av at vi lager en swagger-greie eller tilsvarende

Request:
POST
parametere er personidentifikator (11 siffer) og dato

Response: 
JSON-objekt som inneholder et resultat som er en liste med fom (dato), tom(dato) og grad (heltall)

Alle datoer er på format `yyyy-mm-dd`