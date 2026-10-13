# Jenkins: de la instalare la prima verificare

Codul pluginului, documentația și release-urile curente sunt în [awarely-scan-plugin](https://github.com/awarelyeu/awarely-scan-plugin). Publicarea în Jenkins Update Center așteaptă aprobarea de hosting.

Acest ghid acoperă versiunea stabilă 0.2.0, distribuită pe GitHub. Începe pe un Jenkins de staging și folosește HPI-ul verificat conform [ghidului de release](releases.md).

## 1. Pregătește Jenkins o singură dată

Ai nevoie de Jenkins 2.580.1 sau mai nou, un Java compatibil cu versiunea Jenkins (testăm cu JDK 21) și un agent Linux amd64 sau arm64 care rulează fără root. Controllerul trebuie să aibă zero executori. Build-ul aplicației poate necesita Node.js/npm, Python ori Maven/Java; scannerul citește fișiere deja existente și nu instalează aceste runtime-uri.

Descarcă HPI-ul din [release-ul pluginului](https://github.com/awarelyeu/awarely-scan-plugin/releases/tag/v0.2.0) și urmează [verificarea provenienței și checksum-ului](releases.md). Un plugin poate executa cod pe controller.

În **Manage Jenkins → Plugins → Available plugins**, instalează sau actualizează dependențele: **Credentials**, **Plain Credentials**, **Branch API**, **SCM API**, **Structs**, **Jackson 2 API** și **Commons Compress API**. Jenkins rezolvă și dependențele lor. Pentru Jenkinsfile, instalează și **Pipeline**. Verifică avertismentele de securitate din **Manage Jenkins**.

Apoi deschide **Plugins → Advanced settings → Deploy Plugin**, selectează HPI-ul verificat și încarcă-l. Repornește Jenkins după terminarea build-urilor. Confirmă că **Awarely Scan** apare în **Installed plugins**, fără eroare de încărcare. HPI-ul se distribuie aici; pluginul nu este încă listat în Jenkins Update Center.

În **Manage Jenkins → System → Awarely Scan**, administratorul configurează:

1. **Allow verified tool downloads**, dacă agenții pot descărca CLI-ul și Syft la versiunile fixate. Această opțiune nu rulează automat orice versiune `latest`.
2. Numele complete ale joburilor care pot contacta Monitor, câte unul pe linie. Include numele folderului și ramurii pentru joburile multibranch. Nu se acceptă wildcard-uri.
3. Numele exacte ale agenților dedicați build-urilor de încredere. Separă-i de joburile și PR-urile externe.
4. Originea HTTPS din fișierul de credențiale descărcat din propriul cont Monitor, la **Allowed Monitor API origins**. Aceasta conține protocolul, hostname-ul și eventual portul, fără cale.
5. La **Jobs allowed to synchronize inventory**, aprobă separat joburile de deployment care pot modifica inventarul. Aprobarea pentru check nu permite automat sync.
6. Separat, joburile care pot inventaria sistemul Linux al agentului, la **Jobs allowed to inventory the agent host**.

O scanare locală de aplicație nu necesită cont sau credențiale Monitor. Cache-ul se află în `.awarely-scan` sub directorul agentului, în afara workspace-ului, cu permisiuni doar pentru proprietar. Pentru utilizare fără descărcări, administratorul pregătește arhiva CLI fixată și cache-ul Syft verificat în avans. O arhivă absentă sau modificată oprește pasul.

## 2. Adaugă credențialele pentru verificare

În Monitor, deschide **Setări → Active → Awarely Scan CLI**. Cu acces Pro și MFA, creează o credențială **Check only**, cu expirare, pentru aplicația și sursa dorite. Descarcă fișierul JSON.

În credentials store-ul folderului Jenkins potrivit, adaugă un **Secret file**, încarcă JSON-ul și alege un ID clar, de exemplu `awarely-monitor-check`. Limitează drepturile de configurare a joburilor și accesul la credențiale. Nu pune JSON-ul în Git, tokenul în Jenkinsfile sau credențiala într-o variabilă de mediu.

Pluginul citește Secret file numai pentru o operațiune API autorizată sau pentru verificarea configurației. Preflight îl validează pe controller fără să îl transmită. Operațiunile API reale îl transmit CLI-ului verificat prin standard input. Nu este copiat în workspace și nu este arhivat.

## 3. Verifică întâi configurația

Folosește un job de staging fără pași de deployment. Fă checkout la aplicație și, pentru Syft, construiește aplicația/instalează dependențele înainte de Awarely. În Freestyle, adaugă pasul Awarely și bifează **Validate configuration only (no scan or upload)**. Selectează tipul aplicației, acțiunea dorită și calea relativă la workspace. Iconița de ajutor de lângă fiecare câmp explică ce trebuie completat.

În Pipeline, folosește configurația dorită cu `preflightOnly: true`:

```groovy
awarelyScan collector: 'npm', mode: 'check',
  path: '.', applicationLabel: 'shop',
  credentialsId: 'awarely-monitor-check', preflightOnly: true
```

Citește consola. Preflight verifică agentul real, arhitectura, calea, accesul la manifest/fișier, permisiunile directorului privat de rezultate, cache-ul CLI/politica de descărcare, aprobările administratorului și formatul/accesul la credențială pentru acțiuni API. Nu scanează, nu descarcă, nu contactează Monitor și nu modifică starea sursei. Creează și șterge directoare private temporare.

- **PASS**: verificarea locală indicată a trecut.
- **PENDING**: scanarea reală va trebui să descarce CLI-ul fixat; conexiunea HTTPS nu a fost testată.
- **INFO / ACTION / NOT TESTED**: pregătiri sau verificări care necesită scanarea reală. CLI-ul verifică Syft și conținutul artefactelor la colectare. Dreptul Check/Sync al tokenului, expirarea/revocarea, conexiunea API, cotele și starea sursei se verifică la operațiunea API reală.
- Calea/manifestul lipsă, credențiala invalidă ori o arhivă CLI modificată opresc pasul cu indicații de corectare. Corectează problema și repetă build-ul de staging.

Apoi **debifează opțiunea sau elimină `preflightOnly: true`** și rulează scanarea reală. SUCCESS la preflight nu înseamnă evaluare CVE. Opțiunea afectează numai pasul Awarely: ceilalți pași de build/deployment din job pot rula în continuare.

## 3a. Prima scanare locală

Într-un proiect Freestyle, alege **Add build step → Awarely Scan: inventory, check or synchronize**. Selectează tipul aplicației, o cale relativă la workspace, eticheta inventarului și **Save local inventory only**. Dacă folosești Syft, construiește aplicația înainte de scanare.

În Pipeline poți folosi **Pipeline Syntax → Snippet Generator** sau exemplul:

```groovy
pipeline {
  agent { label 'linux' }
  options {
    timeout(time: 15, unit: 'MINUTES')
    buildDiscarder(logRotator(numToKeepStr: '20'))
    skipStagesAfterUnstable()
  }
  stages {
    stage('Inventory') {
      steps {
        awarelyScan collector: 'npm', mode: 'local',
          path: '.', applicationLabel: 'shop'
      }
    }
  }
}
```

Workspace-ul trebuie să conțină lockfile-ul aplicației. Înlocuiește exemplele cu propriile nume și căi. Pentru citirea nativă a lockfile-ului nu trebuie să rulezi `npm install`. Syft citește dependențele instalate și artefactele construite, deci pregătește-le într-un pas de build anterior.

După build, deschide **Awarely Scan** (scutul cu litera A din meniul lateral) și **Artifacts**. Iconița identifică pluginul; rezultatul evaluării apare în starea buildului și în raport. Inventarul apare la `awarely/<run-id>/inventory.cdx.json`. Artefactele descriu software-ul tău; limitează accesul la ele și perioada de păstrare.

## 4. Verificarea CVE și politica build-ului

Înlocuiește pasul cu:

```groovy
awarelyScan collector: 'npm', mode: 'check',
  path: '.', applicationLabel: 'shop',
  credentialsId: 'awarely-monitor-check',
  severityThreshold: 'HIGH', failOnIncomplete: true,
  policyResult: 'FAILURE'
```

`check` trimite identitățile normalizate ale pachetelor, versiunile și dovezile către API-ul Monitor configurat. Nu salvează inventarul și nu schimbă alertele. Jenkins păstrează raportul JSON complet și afișează un rezumat cu perioada, severitatea, precizia și componentele neevaluate.

- `REPORT_ONLY` afișează rezultatele fără prag de severitate.
- `CRITICAL`, `HIGH`, `MEDIUM` sau `LOW` declanșează politica aleasă dacă există o potrivire confirmată pe versiune la acel nivel sau mai sus.
- Potrivirile numai pe produs rămân vizibile, dar nu declanșează pragul pentru versiuni confirmate.
- `failOnIncomplete: true` declanșează separat politica pentru inventar parțial sau componente neevaluate, inclusiv când nu există potriviri CVE. În modul local se referă doar la acoperirea inventarului.
- Erorile de intrare, autentificare, API ori verificare a uneltelor opresc pasul; nu sunt transformate în rezultate cu zero CVE.

`policyResult: 'FAILURE'` păstrează inventarul și raportul, marchează FAILURE și oprește execuția normală a pasului. Pașii Pipeline următori nu rulează decât dacă Jenkinsfile-ul interceptează intenționat eroarea, de exemplu cu `catchError` sau `try/catch`.

`policyResult: 'UNSTABLE'` este valoarea implicită, inclusiv pentru joburile existente. Raportează depășirea politicii și lasă pasul să se termine. În Declarative Pipeline, `skipStagesAfterUnstable()` împiedică etapele ulterioare, inclusiv deployment-ul, după un astfel de rezultat. `REPORT_ONLY` nu dezactivează politica separată pentru acoperire incompletă. Nicio potrivire nu înseamnă automat că aplicația este sigură.

## 5. Python, Java și celelalte tipuri de inventar

Pentru un virtual environment creat în directorul aplicației:

```groovy
awarelyScan collector: 'python-syft', mode: 'check',
  path: 'services/reports', applicationLabel: 'reports',
  credentialsId: 'awarely-monitor-check', severityThreshold: 'HIGH'
```

Creează mediul și instalează dependențele înainte, prin pașii obișnuiți ai proiectului. Varianta `python` citește requirements.txt și raportează inventar parțial, fiindcă nu cunoaște toate dependențele instalate.

Pentru Java:

```groovy
awarelyScan collector: 'java', mode: 'check',
  path: 'services/shipping', applicationLabel: 'shipping',
  credentialsId: 'awarely-monitor-check', severityThreshold: 'HIGH'
```

Construiește JAR/WAR/EAR și pregătește dependențele înainte de scanare. Un director numai cu surse nu reprezintă neapărat aplicația construită. Syft urmează versiunea CLI fixată de plugin; nu există întrebări interactive sau o setare pentru executarea oricărei versiuni Syft.

Alte variante:

| `collector` | Ce selectezi |
| --- | --- |
| `npm-syft` | Directorul aplicației Node.js cu dependențele instalate |
| `linux` | Pachetele sistemului Linux al agentului; `allPackages: true` extinde inventarul |
| `other` | Fișiere NuGet/Go cu verificare CVE; Composer/RubyGems/Cargo rămân neevaluate |
| `import` | Fișier CycloneDX extern cu pachetele aplicației |
| `existing` | Un inventar Awarely existent, fără repetarea scanării |

Scanarea Linux descrie **agentul Jenkins**. Nu scanează automat serverul de producție sau imaginea unui container.

## 6. Sincronizarea inventarului implementat

Folosește o credențială separată **Sync only** și un job de deployment de încredere. Fiecare sursă aplicație/mediu trebuie să aibă un singur job care o actualizează. Sincronizează inventarul artefactului implementat după deployment-ul reușit; un simplu build ar trebui, de regulă, să folosească `check`.

```groovy
awarelyScan collector: 'java', mode: 'sync',
  path: 'services/shipping', applicationLabel: 'shipping-production',
  credentialsId: 'awarely-monitor-sync'
```

Eticheta descrie SBOM-ul; credențiala stabilește aplicația și sursa destinație. Sync înlocuiește imediat numai acea sursă și le păstrează pe celelalte. Inventarele parțiale sau goale sunt refuzate. Pluginul serializează sincronizările sale, persistă ordinea build-urilor și refuză un build mai vechi ori alt job care încearcă să scrie aceeași sursă. Serverul verifică separat revizia și cheia de idempotency.

Nu pune sync într-un `retry` automat. După timeout, restart de controller sau pierderea răspunsului, verifică sursa în Monitor înainte de repetare. CLI-ul păstrează aceeași revizie și cheie pentru propriile retry-uri de transport, limitate. Pluginul nu reia automat un proces întrerupt de restart-ul controllerului.

## 6a. Compară build-urile

De la al doilea build comparabil, deschide **Build → Awarely Scan → Changes since the previous build**. Nu trebuie configurată o acțiune suplimentară și nu se face alt apel Monitor.

Pluginul compară cu build-ul anterior păstrat al aceluiași job, pentru același colector, cale, etichetă, domeniu de scanare, CLI fixat și destinație aplicație/sursă. Pentru Linux trebuie să fie același nume de agent. Păstrează calea/eticheta stabile și folosește etichete distincte pentru scanări separate în același job. Rotirea credențialei pentru aceeași destinație păstrează comparația.

Vezi componente adăugate/eliminate; o schimbare de versiune apare ca o eliminare și o adăugare. Pentru două verificări complete, fără componente neevaluate și cu aceeași durată a perioadei evaluate, apar și potriviri pe componente **observate acum** sau **care nu mai apar**, inclusiv versiunea și precizia. Schimbarea catalogului ori a datelor perioadei este indicată explicit. Dispariția unei potriviri nu demonstrează remedierea: se poate schimba pachetul, catalogul ori perioada evaluată.

Totalurile folosesc SBOM-urile și rapoartele complete păstrate, nu doar primele 100 de potriviri afișate. Tabelul arată maximum 100 de schimbări; JSON-urile originale ale ambelor build-uri păstrează dovezile complete. Politica build-ului se bazează pe toate potrivirile curente confirmate pe versiune, nu pe diferențe.

Primul build, rezultatele vechi fără metadate de comparație, artefactele șterse/expirate, pașii dublați fără o identitate distinctă, un build anterior încă activ sau schimbarea domeniului afișează motivul pentru care comparația nu este disponibilă. Inventarele parțiale opresc comparația componentelor. Evaluările parțiale/neevaluate opresc doar comparația CVE, dacă inventarele sunt complete. Pluginul nu caută în tăcere un build mai vechi care a trecut.

## 7. Rezolvarea problemelor și actualizările

| Mesaj/situație | Ce faci |
| --- | --- |
| Director absent | Corectează calea relativă la workspace și verifică checkout-ul/build-ul. Căile absolute și `..` sunt refuzate. |
| Unealtă absentă | Administratorul permite descărcarea verificată sau pregătește cache-ul versiunii fixate. |
| Job, agent sau destinație neaprobate | Administratorul aprobă configurația de încredere dorită. |
| Inventar Python parțial | Folosește `python-syft` după instalarea dependențelor în virtual environment. |
| Ecosistem neevaluat | Componenta există în inventar, dar serviciul nu îi evaluează încă CVE-urile. |
| Alt job/build mai nou deține sursa | Folosește jobul desemnat. După redenumirea jobului ori resetarea numerotării, administratorul trebuie să revizuiască legătura persistentă. |
| Sync întrerupt | Verifică inventarul salvat în Monitor înainte de repetare. |

Actualizează deliberat pluginul dintr-un release verificat și testează întâi pe staging. Versiunile și digesturile CLI sunt fixate de release-ul pluginului, iar Syft urmează CLI-ul. Build-urile obișnuite nu rulează `update`, installere sau `latest`.

Rotește/revocă credențialele când se schimbă joburile ori permisiunile. Păstrează numai artefactele necesare. Dacă un build întrerupt nu își poate șterge fișierele private, curăță sau recreează agentul izolat înainte de reutilizare.

Verificările respectă limita API a contului Monitor (în prezent șase verificări pe minut, cu un număr limitat de operații simultane). Decalează joburile paralele; nu crea credențiale suplimentare pentru a ocoli limita contului. O cerere refuzată produce o eroare vizibilă și păstrează SBOM-ul local.

## Progresul în consolă și logarea detaliată

Fiecare pas Awarely explică validarea inputului, pregătirea uneltelor verificate, colectarea, așteptarea API-ului, acoperirea, locația rapoartelor, politica și durata. Mesajele sunt aceleași în Pipeline și Freestyle. Un mesaj de așteptare indică o operație aflată încă în termenul permis; nu trimite o cerere nouă. Finalizarea pasului nu anulează un rezultat UNSTABLE/FAILURE anterior al buildului.

Mesajele normale sunt suficiente pentru utilizarea zilnică. Pentru detalii despre verificarea cache-ului, inputul selectat și modul de rulare a uneltelor, bifează **Detailed logging (safe operational details)** în Freestyle sau adaugă opțiunea în Pipeline:

```groovy
awarelyScan collector: 'npm', mode: 'check', path: 'app',
    credentialsId: 'monitor-check', detailedLogging: true
```

Calea este relativă la workspace. Nu pune secrete în etichete sau căi; folosește credentiale Jenkins de tip Secret file. Ambele niveluri ascund ieșirea brută a proceselor, variabilele de mediu, conținutul credentialelor și corpurile răspunsurilor API. Pluginul acceptă doar coduri CLI cunoscute, fără date atașate, și afișează mesaje din propriul catalog limitat. Codurile necunoscute sunt ignorate; rezultatul depinde de exit code și de artefactele validate.

**INSUFFICIENT_SCOPE** indică un credential fără dreptul cerut: pentru sincronizare folosește un credential Sync. **INVALID_CREDENTIAL** poate însemna invalid, expirat sau revocat: înlocuiește Secret file în Jenkins. **INVENTORY_CONFLICT** indică o sursă modificată de altă operație: inspectează starea curentă. Dacă sync expiră sau confirmarea nu poate fi salvată, sincronizarea poate fi deja efectuată. Nu relansa automat.

Erorile previzibile de inventar invalid, cale cu ../ sau symlink spre exterior opresc pasul cu explicație și soluție, fără stack trace Java. Defecțiunile interne neașteptate necesită în continuare investigația administratorului. Politicile de acoperire și severitate rămân neschimbate.

## Biblioteci NuGet/.NET și module Go

1. Alege **NuGet / Go and other application ecosystems (Syft)** sau `collector: 'other'` în Pipeline.
2. Execută restore/build separat, într-o etapă anterioară. Pentru .NET, păstrează `packages.lock.json`, `project.assets.json` sau `.deps.json` publicat. Pentru Go, păstrează `go.mod`/`go.sum` sau un executabil cu informații despre module. Selectează directorul din workspace care conține aceste fișiere.
3. Alege **Check** și credentialul Monitor de tip **Secret file** aprobat. Începe cu **Validate configuration only**, apoi dezactivează opțiunea pentru verificarea reală.
4. Deschide **Awarely Scan** și artefactele JSON. Potrivirile confirmate pe versiune folosesc politica de severitate existentă; versiunile/intervalele neacceptate apar ca neevaluate și activează `failOnIncomplete` dacă este bifat.

```groovy
stage('Check dependency inventory') {
    awarelyScan collector: 'other', mode: 'check',
        path: 'app', applicationLabel: 'My .NET or Go application',
        credentialsId: 'monitor-check', severityThreshold: 'HIGH',
        policyResult: 'FAILURE', failOnIncomplete: true
}
```

Înlocuiește `app` și `monitor-check` cu directorul din workspace și ID-ul credentialului Jenkins. Administratorul trebuie să aprobe exact acest job și agentul de încredere înainte de cereri către Monitor. Scanerul nu execută restore/build și nu instalează SDK-uri.

Monitor cere un aviz pentru ecosistemul și pachetul exact. La NuGet, numele nu ține cont de majuscule; la Go, calea păstrează majusculele și sufixul versiunii majore, precum `/v2`. Sunt comparate explicit reviziile/prerelease-urile NuGet și versiunile SemVer/pseudo Go. Intervalele neacceptate produc potriviri pe produs și `ADVISORY_RANGE_NOT_EVALUATED`, fără confirmare pe versiune. Build-urile Go dirty/development rămân neevaluate. Modulul principal compilat fără o versiune de release este păstrat cu versiune necunoscută, iar inventarul devine parțial; Check rămâne disponibil, însă Sync este refuzat. Nu sunt incluse biblioteca standard/toolchain Go, deducerea vulnerabilității runtime-ului .NET din numele NuGet sau analiza funcțiilor executate. Composer, RubyGems și Cargo păstrează doar inventar/sync.

După remedierea dependențelor, execută din nou restore/build și jobul. Verifică versiunile și acoperirea înainte să interpretezi scăderea numărului de potriviri. Perioada de evaluare a avizelor pentru aplicații se aplică în continuare.
