package org.example.ga;

import java.util.*;
import java.util.stream.Collectors;

public class Ecosystem {
    private List<Creature> population;
    private EvolutionaryOperators operators;
    private CreatureProblem problem;

    // === Plant system ===
    private double plants;          // stock attuale
    private double capacityK;       // capacità portante
    private double growthR;         // tasso logistico base per ciclo (0..1)
    private int plantPerHerb;       // costo piante per 1 "successo" erbivoro
    private boolean seasonal;       // stagionalità on/off
    private int cycleCounter;       // contatore cicli per stagionalità
    private double regenMinPerCycle; // ricaccio minimo per ciclo (seme), in unità assolute

    // === Rapporto iniziale carnivori ===
    private double initialCarnivoreShare = 0.2; // default: 1 carnivoro ogni 4 erbivori

    public Ecosystem() {
        this.population = new ArrayList<>();
        this.operators = new EvolutionaryOperators();

        // default
        this.plants = 8000;
        this.capacityK = 12000;
        this.growthR = 0.40;
        this.plantPerHerb = 2;   // leggermente più caro per contenere consumi
        this.seasonal = true;
        this.cycleCounter = 0;

        // seed: 0.5% di K a ciclo (~60 se K=12000)
        this.regenMinPerCycle = 0.005 * this.capacityK;
    }

    // Config quota iniziale carnivori
    public void setInitialCarnivoreShare(double share) {
        this.initialCarnivoreShare = Math.max(0.0, Math.min(1.0, share));
    }

    // Config piante (retro-compatibile)
    public void configurePlants(double startStock, double capacityK, double growthR, int plantPerHerb, boolean seasonal) {
        this.plants = Math.max(0, startStock);
        this.capacityK = Math.max(1, capacityK);
        this.growthR = Math.max(0, Math.min(1, growthR));
        this.plantPerHerb = Math.max(1, plantPerHerb);
        this.seasonal = seasonal;
        this.cycleCounter = 0;
        // mantieni il seed già impostato dal costruttore
    }

    // Config piante con seed esplicito (frazione di K per ciclo)
    public void configurePlants(double startStock, double capacityK, double growthR,
                                int plantPerHerb, boolean seasonal, double regenMinPerCycleFraction) {
        this.plants = Math.max(0, startStock);
        this.capacityK = Math.max(1, capacityK);
        this.growthR = Math.max(0, Math.min(1, growthR));
        this.plantPerHerb = Math.max(1, plantPerHerb);
        this.seasonal = seasonal;
        this.cycleCounter = 0;
        this.regenMinPerCycle = Math.max(0, regenMinPerCycleFraction) * this.capacityK;
    }

    // Crescita logistica + ricaccio minimo (seed) + stagionalità
    private void growPlants() {
        double r = growthR;
        if (seasonal) {
            // stagionalità dolce: oscillazione ±20% sul tasso
            double factor = 1.0 + 0.2 * Math.sin(2 * Math.PI * (cycleCounter % 12) / 12.0);
            r = growthR * factor;
        }
        // logistic + seed: P <- P + r * P * (1 - P/K) + seed
        plants = plants + r * plants * (1.0 - (plants / capacityK)) + regenMinPerCycle;

        // clamp
        if (plants < 0) plants = 0;
        if (plants > capacityK) plants = capacityK;
    }

    // Consumo di 1 “porzione” da parte di un erbivoro (per 1 successo)
    public boolean consumePlantPortion() {
        if (plants >= plantPerHerb) {
            plants -= plantPerHerb;
            return true;
        }
        return false;
    }

    // Getters per gli operatori/stampe
    public int getPlantPerHerb() { return plantPerHerb; }
    public int getPlants() { return (int)Math.floor(plants); }
    public int getPlantCapacity() { return (int)Math.floor(capacityK); }

    public void setVerboseMode(boolean verbose) {
        this.operators.setVerboseMode(verbose);
    }

 // Ecosystem.java — dentro la classe Ecosystem

 // Overload 1: come prima (random carn/erb)
 public void initializePopulation(int size, CreatureProblem problem) {
     this.problem = problem;
     this.population = operators.initializePopulation(size, problem); // usa la versione “vecchia”
 }

 // Overload 2: con frazione fissa (es. 0.20 = 1 carnivoro ogni 4 erbivori)
 public void initializePopulation(int size, CreatureProblem problem, double carnivoreFraction) {
     this.problem = problem;
     this.population = operators.initializePopulation(size, problem, carnivoreFraction);
 }

 // Overload 3: con rapporto intero (es. 1:4)
 public void initializePopulation(int size, CreatureProblem problem, int carnivori, int erbivori) {
     this.problem = problem;
     this.population = operators.initializePopulation(size, problem, carnivori, erbivori);
 }

    
    private void printTraitDistribution() {
        List<Creature> alive = getAliveCreatures();
        int n = alive.size();
        if (n == 0) {
            System.out.println("Tratti: n/a (nessuna creatura viva)");
            return;
        }

        // counts[t] = numero di individui che hanno il tratto t
        int[] counts = new int[6]; // indicizziamo 1..5 per sicurezza
        int noTrait = 0;

        for (Creature c : alive) {
            List<Integer> ts = c.getActiveTraits();
            if (ts == null || ts.isEmpty()) {
                noTrait++;
                continue;
            }
            // activeTraits non ha duplicati per creatura
            for (Integer t : ts) {
                if (t != null && t >= 1 && t <= 5) {
                    counts[t]++;
                }
            }
        }

        // Stampa 1..4 (i tratti in uso)
        String fmt = "Tratto %d: %.1f%%";
        System.out.printf((fmt) + ", " + (fmt) + ", " + (fmt) + ", " + (fmt) + "%n",
                1, (counts[1] * 100.0) / n,
                2, (counts[2] * 100.0) / n,
                3, (counts[3] * 100.0) / n,
                4, (counts[4] * 100.0) / n
        );

        // Facoltativo: mostra anche quanti non hanno alcun tratto
        System.out.printf("Senza tratti: %.1f%%%n", (noTrait * 100.0) / n);
    }

    public void runLifeCycle() {
        // 1) crescita piante a inizio ciclo
        growPlants();
        cycleCounter++;

        // 2) invecchiamento & filtro vivi
        List<Creature> survivors = new ArrayList<>();
        for (Creature creature : population) {
            if (creature.isAlive() && creature.incrementLongevity()) {
                survivors.add(creature);
            }
        }

        // 3) applica ciclo vita con ecosistema (per cibo & incontri)
        population = operators.applyLifeCycle(survivors, this);

        System.out.printf(
            "Ciclo completato - Pop: %d, Morti fame: %d, Riproduzioni: %d%n",
            population.size(),
            operators.getStarvationDeaths(),
            operators.getReproductionCount()
        );
        printTraitDistribution();
    }

    public List<Creature> getAliveCreatures() {
        return population.stream().filter(Creature::isAlive).collect(Collectors.toList());
    }

    public int getAliveCount() {
        return (int) population.stream().filter(Creature::isAlive).count();
    }

    public void printPopulationStats() {
        List<Creature> alive = getAliveCreatures();
        if (alive.isEmpty()) {
            System.out.println("Nessuna creatura viva nella popolazione.");
            System.out.printf("Piante: %d / %d (per erbivoro: %d)%n", getPlants(), getPlantCapacity(), getPlantPerHerb());
            return;
        }

        long carnivores = alive.stream().filter(Creature::isCarnivore).count();
        long herbivores = alive.stream().filter(Creature::isHerbivore).count();

        double avgSmell = alive.stream().mapToInt(Creature::getSmell).average().orElse(0);
        double avgSize  = alive.stream().mapToInt(Creature::getSize).average().orElse(0);
        double avgLong  = alive.stream().mapToInt(Creature::getLongevity).average().orElse(0);
        double avgHear  = alive.stream().filter(Creature::isHerbivore).mapToInt(Creature::getHearing).average().orElse(0);
        double avgVis   = alive.stream().filter(Creature::isCarnivore).mapToInt(Creature::getVision).average().orElse(0);

        System.out.printf("Popolazione: %d creature (Carn: %d, Herb: %d)%n", alive.size(), carnivores, herbivores);
        System.out.printf("Medie - Sml: %.1f, H(erb): %.1f, V(carn): %.1f, Size: %.1f, Long: %.1f%n",
                avgSmell, avgHear, avgVis, avgSize, avgLong);

        int minSmell = alive.stream().mapToInt(Creature::getSmell).min().orElse(0);
        int maxSmell = alive.stream().mapToInt(Creature::getSmell).max().orElse(0);
        int minSize  = alive.stream().mapToInt(Creature::getSize).min().orElse(0);
        int maxSize  = alive.stream().mapToInt(Creature::getSize).max().orElse(0);

        System.out.printf("Smell: %d-%d, Taglia: %d-%d%n", minSmell, maxSmell, minSize, maxSize);
        System.out.printf("Piante: %d / %d (per erbivoro: %d)%n", getPlants(), getPlantCapacity(), getPlantPerHerb());
    }

    // === Medie utili per stampa finale ===
    public double getAveragePerception() { // alias: Smell
        return getAliveCreatures().stream().mapToInt(Creature::getSmell).average().orElse(0);
    }

    public double getAverageSize() {
        return getAliveCreatures().stream().mapToInt(Creature::getSize).average().orElse(0);
    }

    public double getAverageLongevity() {
        return getAliveCreatures().stream().mapToInt(Creature::getLongevity).average().orElse(0);
    }

    public double getAverageTotalStats() {
        // Somma = Smell + Size + (Hearing o Vision in base alla dieta)
        return getAliveCreatures().stream()
                .mapToInt(c -> c.getSmell() + c.getSize() + (c.isHerbivore() ? c.getHearing() : c.getVision()))
                .average().orElse(0);
    }
}
