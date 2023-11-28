Spaπ eksponerer sykepengeperioder til eksterne konsumenter.

I første omgang er dette kun Fellesordningen for AFP.

---
Overordnet systemarkitekturskisse
---

```mermaid
---
title: FO AFP henter data
---
flowchart LR
    FO-AFP -->|https| Spaπ
    Spaπ -->|https| PDL
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
title: Use Case FO AFP henter data; Parametere er fødselsnummer (eller annen tilsvarende personidentifikator), tidligeste sluttdato for sykdomsperiode, seneste startdato for sykdomsperiode, og virksomhetsnummer for arbeidsgiveren vi er interessert i.
---
flowchart TB
    fo-afp -->|Gi meg data | Spaπ
    Spaπ -->|Gi med historiske identer| PDL
    PDL -->|Vær så god| Spaπ
    Spaπ -->|I en loop: Gi meg data for alle disse identene og resten av paramterne| spøkelse
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
    FO-AFP ->> Spaπ: Gi meg data for personidentifikator, tidligste sluttdato, seneste startdato, og organisasjon 
    activate Spaπ
    Spaπ ->> PDL: Gi meg historiske identer for personidentifikator
    PDL -->> Spaπ: Her en liste med identer
    loop Hver personidentifikator fra PDL
    Spaπ ->> Spøkelse: Gi meg data for identifikator, datoer og organisasjon
    activate Spøkelse
    Spøkelse ->> SpøkelseDB: Gi meg spleis-data for identifikator, datoer og organisasjon
    Spøkelse ->> InfotrygdReplikering: Gi meg infotrygd-data for identifikator, datoer og organisasjon
    Spøkelse ->> Spøkelse: Slå sammen data på tvers av kilde
    Spøkelse -->> Spaπ: Her er dataen
    deactivate Spøkelse
    end
    Spaπ ->> Spaπ: Slå sammen data på tvers av identer
    Spaπ ->> Sporingslogg: Dette er data vi ønsker å gi til FO AFP
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
