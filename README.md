# GA-Genetic-Planet 🌍🧬

Benvenuto in **GA-Genetic-Planet**, un simulatore di ecosistema evolutivo sviluppato in Java. Il progetto simula la sopravvivenza, l'interazione e l'evoluzione di una popolazione di creature (divise in erbivori e carnivori) all'interno di un ambiente con risorse limitate.

Il simulatore si appoggia a **jMetal** per la modellizzazione del problema evolutivo, arricchito da regole ecologiche personalizzate.

---

## 🚀 Come Funziona il Simulatore

La simulazione si sviluppa in cicli temporali (generazioni) e si basa su tre pilastri principali:

### 1. Le Creature e i loro Geni
Ogni creatura possiede tre geni principali che determinano le sue capacità fisiche e percettive, con un **limite massimo combinato (budget) di 20 punti**:
* **Smell (Olfatto)**: Determina l'efficacia nel trovare cibo.
* **Size (Taglia)**: Determina la forza nei combattimenti, ma anche il consumo energetico (più sei grande, più cibo ti serve per sopravvivere).
* **XGene (Percezione secondaria)**: 
  * Per gli **Erbivori** rappresenta l'**Udito** (per rilevare i predatori).
  * Per i **Carnivori** rappresenta la **Visione** (per scovare le prede).

Le creature possono inoltre sbloccare fino a 3 **tratti speciali** (es. bonus all'olfatto, alla taglia, o turni di ricerca cibo extra).

### 2. L'Ecosistema (Le Piante)
* Gli erbivori si nutrono di piante.
* La ricrescita delle piante segue un modello logistico condizionato dalla stagionalità (il tasso varia ciclicamente) e da un valore minimo di ricrescita (semi).

### 3. Ciclo di Vita ed Evoluzione
Durante ogni ciclo:
1. **Invecchiamento**: Le creature invecchiano ad ogni ciclo. Raggiunta la vecchiaia massima, muoiono.
2. **Foraggiamento**: Gli erbivori consumano piante per accumulare energia. I carnivori cercano di cacciare gli erbivori.
3. **Caccia e Combattimento**:
   * **Fuga**: Se un erbivoro ha un udito molto superiore alla visione del predatore ($Udito - Visione \ge 2$), riesce a scappare senza combattere.
   * **Scontro**: Altrimenti combattono. Vince la creatura con taglia maggiore (50% a testa in caso di parità). Chi perde viene eliminato.
4. **Sopravvivenza e Morte**: Chi non ha accumulato abbastanza cibo muore di fame.
5. **Riproduzione e Mutazione**: Le creature sopravvissute con abbastanza energia si riproducono. I figli ereditano i tratti dei genitori con una probabilità di **mutazione del 40%**.

---

## 📂 Struttura del Progetto

Il codice è organizzato nel package `org.example.ga`:

* 🎮 **[EvolutionSimulation.java](org/example/ga/EvolutionSimulation.java)**: Il punto di partenza (`main`). Inizializza l'ambiente e avvia i cicli di simulazione.
* 🦁 **[Creature.java](org/example/ga/Creature.java)**: Rappresenta l'individuo con i suoi geni, tratti e stato vitale.
* 🌿 **[Ecosystem.java](org/example/ga/Ecosystem.java)**: Gestisce le risorse vegetali e l'andamento stagionale.
* 🛠️ **[EvolutionaryOperators.java](org/example/ga/EvolutionaryOperators.java)**: Implementa la logica di foraggiamento, caccia, combattimento, riproduzione e mutazione.
* 📐 **[CreatureProblem.java](org/example/ga/CreatureProblem.java)**: Modella il problema per jMetal.
