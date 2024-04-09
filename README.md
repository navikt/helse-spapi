Spaπ eksponerer sykepengeperioder til eksterne konsumenter.

I første omgang deles dette med privat og offentlig AFP.

---
Overordnet systemarkitekturskisse
---

```mermaid
---
title: FO AFP henter data
---
%%{init: {'theme':'forest'}}%%
flowchart TB
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
%%{init: {'theme':'forest'}}%%
flowchart TB
    spøkelse -->|leser fra kafka| kafka[/tbd.utbetaling/] --> database[(spøkelse-db)]
```

```mermaid
---
title: Use Case FO AFP henter data; Parametere er fødselsnummer (eller annen tilsvarende personidentifikator), tidligeste sluttdato for sykdomsperiode, seneste startdato for sykdomsperiode, og virksomhetsnummer for arbeidsgiveren vi er interessert i.
---
%%{init: {'theme':'forest'}}%%
flowchart LR
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
%%{init: {'theme':'forest'}}%%
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
[Swagger Test](https://spapi.ekstern.dev.nav.no/swagger)

[Swagger Produksjon](https://spapi.nav.no/swagger)