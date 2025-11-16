package org.example.ga;

import java.util.*;

public class EvolutionaryOperators {
    private Random random;

    // === Parametri generali ===
    private static final int MAX_TOTAL_STATS = 20;        // Size + Smell + (H|V)
    private static final int MAX_POPULATION_SIZE = 10000; // Safety cap

    // Vision vs Hearing
    private static final int HV_HARD_DELTA = 1;           // soglia netta
    private static final double HV_SOFT_STEP = 0.15;      // gradiente zona morbida

    // === Scoring per ciclo ===
    private static final int HERB_SCORE_PER_FOOD = 100;
    private static final int CARN_SCORE_PER_FOOD = 100;    // leggermente più basso
    private static final int CARN_SCORE_PER_KILL = 150;

    // base punti “unità soglia” (verrà scalata per taglia)
    private static final int THRESHOLD_UNIT = 100;

    // === Statistiche ===
    private int starvationDeaths; // morti per fame (non per predazione)
    private int escapeCount;
    private int reproductionCount;
    private int carnivoreCount;
    private int herbivoreCount;
    private int herbivoreSizeWins;
    private boolean verboseMode;

    public EvolutionaryOperators() {
        this.random = new Random();
        this.starvationDeaths = 0;
        this.escapeCount = 0;
        this.reproductionCount = 0;
        this.carnivoreCount = 0;
        this.herbivoreCount = 0;
        this.verboseMode = false;
    }

    public void setVerboseMode(boolean verbose) {
        this.verboseMode = verbose;
    }

    // === NUOVI METODI DI INIZIALIZZAZIONE POPOLAZIONE ===
    private double clamp01(double x) { return Math.max(0.0, Math.min(1.0, x)); }

    /** Inizializza popolazione con frazione di carnivori (es. 0.20 = 20%) */
    public List<Creature> initializePopulation(int populationSize, CreatureProblem problem, double carnivoreFraction) {
        carnivoreFraction = clamp01(carnivoreFraction);
        int actual = Math.min(populationSize, MAX_POPULATION_SIZE);

        int carnivores = (int) Math.round(actual * carnivoreFraction);
        int herbivores = actual - carnivores;

        List<Creature> pop = new ArrayList<>(actual);

        for (int i = 0; i < carnivores; i++) {
            pop.add(generateRandomCreature(problem, true));
        }
        for (int i = 0; i < herbivores; i++) {
            pop.add(generateRandomCreature(problem, false));
        }

        Collections.shuffle(pop, random);
        return pop;
    }

    /** Convenienza: rapporto carnivori:erbivori, es. (1,4) -> 0.20 */
    public List<Creature> initializePopulation(int populationSize, CreatureProblem problem, int carnivori, int erbivori) {
        int total = Math.max(1, carnivori + erbivori);
        double carnFrac = (double) carnivori / (double) total;
        return initializePopulation(populationSize, problem, carnFrac);
    }

    /** Versione originale: random tra carn/erb. */
    public List<Creature> initializePopulation(int populationSize, CreatureProblem problem) {
        List<Creature> pop = new ArrayList<>();
        int actual = Math.min(populationSize, MAX_POPULATION_SIZE);
        for (int i = 0; i < actual; i++) {
            pop.add(generateRandomCreature(problem));
        }
        return pop;
    }

    public Creature generateRandomCreature(CreatureProblem problem) {
        boolean carn = random.nextBoolean();
        return generateRandomCreature(problem, carn);
    }

    /** Overload per generare carnivoro/erbivoro forzato. */
    public Creature generateRandomCreature(CreatureProblem problem, boolean isCarnivore) {
        int smell, size, xgene;
        do {
            smell = 1 + random.nextInt(10);
            size  = 1 + random.nextInt(10);
            xgene = 1 + random.nextInt(10);
        } while (smell + size + xgene > MAX_TOTAL_STATS);

        List<Integer> genes = List.of(smell, size, xgene);
        Creature c = new Creature(problem, genes);
        c.setDiet(isCarnivore);
        c.setPrintTraitMessages(false);
        c.enforceBudget();
        return c;
    }

    /** PIPELINE principale con sistema di cibo e scontri */
    public List<Creature> applyLifeCycle(List<Creature> population, Ecosystem eco) {
        List<Creature> nextPop = new ArrayList<>();
        herbivoreSizeWins = 0;
        starvationDeaths = 0;
        escapeCount = 0;
        reproductionCount = 0;
        carnivoreCount = 0;
        herbivoreCount = 0;

        Collections.shuffle(population, random);

        // Split per dieta
        List<Creature> herbs = new ArrayList<>();
        List<Creature> carns = new ArrayList<>();
        for (Creature c : population) {
            if (c.isHerbivore()) herbs.add(c); else carns.add(c);
        }

        // === 1) Foraging/scavenge → accumulo score
        Map<Creature,Integer> score = new HashMap<>();
        Map<Creature,Integer> forageAttempts = new HashMap<>(); // NEW: tentativi usati dagli erbivori

        // Erbivori: foraggiano fino a raggiungere SOGLIA RIPRODUZIONE, registrando i tentativi usati
        for (Creature h : herbs) {
            if (!h.isAlive()) continue;
            int targetRepro = herbReproThreshold(h);
            int s = herbivoreForagingAccumulate(h, eco, targetRepro, forageAttempts);
            score.put(h, s);
        }

        // Carnivori: uno scavenge
        for (Creature k : carns) {
            if (!k.isAlive()) continue;
            score.put(k, carnivoreSingleScavengeScore(k));
        }

        // === 2) Caccia: esposizioni legate ai tentativi di foraggiamento
        List<Creature> availableHerbs = new ArrayList<>();
        for (Creature h : herbs) if (h.isAlive()) availableHerbs.add(h);
        Collections.shuffle(availableHerbs, random);

        // budget = tentativi usati nel foraggiamento (se 0, non resta nel pool)
        Map<Creature, Integer> exposuresLeft = new HashMap<>();
        for (Creature h : availableHerbs) {
            exposuresLeft.put(h, Math.max(0, forageAttempts.getOrDefault(h, 0)));
        }

        // paracadute anti-loop
        int safety = 0;
        int maxAttempts = Math.max(1, availableHerbs.size() * 30);

        while (!availableHerbs.isEmpty() && safety++ < maxAttempts) {
            // carnivori vivi
            List<Creature> aliveCarns = new ArrayList<>();
            for (Creature k : carns) if (k.isAlive()) aliveCarns.add(k);
            if (aliveCarns.isEmpty()) break;

            // pick erbivoro ancora con esposizioni residue
            int idx = random.nextInt(availableHerbs.size());
            Creature h = availableHerbs.get(idx);
            Integer left = exposuresLeft.get(h);

            // se morto o senza esposizioni, rimuovi e continua
            if (!h.isAlive() || left == null || left <= 0) {
                availableHerbs.remove(idx);
                exposuresLeft.remove(h);
                continue;
            }

            Creature k = aliveCarns.get(random.nextInt(aliveCarns.size()));
            FightResult fr = resolveEncounter(k, h);
            int sizeCarnEff = effectiveTripletForChecks(k)[1];
            int sizeHerbEff = effectiveTripletForChecks(h)[1];
            boolean herbWonBySize = (fr.winner == h) && (sizeHerbEff > sizeCarnEff);
            if (herbWonBySize) herbivoreSizeWins++;

            if (fr.fugitive == h) {
                escapeCount++;
                // consuma 1 esposizione anche se scappa
                int newLeft = left - 1;
                if (newLeft <= 0) {
                    availableHerbs.remove(idx);
                    exposuresLeft.remove(h);
                } else {
                    exposuresLeft.put(h, newLeft);
                }
            } else if (fr.winner == k) {
                int cur = score.getOrDefault(k, 0);
                score.put(k, cur + CARN_SCORE_PER_KILL);
                h.kill();
                // ucciso → esce subito dal pool
                availableHerbs.remove(idx);
                exposuresLeft.remove(h);
            } else {
                // esito alternativo: consuma comunque 1 esposizione
                int newLeft = left - 1;
                if (newLeft <= 0 || !h.isAlive()) {
                    availableHerbs.remove(idx);
                    exposuresLeft.remove(h);
                } else {
                    exposuresLeft.put(h, newLeft);
                }
                if (fr.loser == k) k.kill();
            }
        }
        System.out.printf(
                "CACCIA — fughe: %d, vittorie erbivore (preda troppo grande): %d%n",
                escapeCount, herbivoreSizeWins
            );
        // opzionale: se safety == maxAttempts, potresti loggare un warning

        // === 3) Esiti: soglie dipendenti dalla taglia
        List<Creature> breedingPool = new ArrayList<>();

        for (Creature h : herbs) {
            if (!h.isAlive()) continue;
            int s = score.getOrDefault(h, 0);
            int sSurv = herbSurviveThreshold(h);
            int sRepr = herbReproThreshold(h);

            if (s >= sSurv) {
                nextPop.add(h);
                if (s >= sRepr) breedingPool.add(h);
            } else {
                h.kill(); starvationDeaths++;
            }
            herbivoreCount++;
        }

        for (Creature k : carns) {
            if (!k.isAlive()) continue;
            int s = score.getOrDefault(k, 0);
            int sSurv = carnSurviveThreshold(k);
            int sRepr = carnReproThreshold(k);

            if (s >= sSurv) {
                nextPop.add(k);
                if (s >= sRepr) breedingPool.add(k);
            } else {
                k.kill(); starvationDeaths++;
            }
            carnivoreCount++;
        }

        // === 4) Riproduzione
        applyReproduction(breedingPool, nextPop);

        if (nextPop.size() > MAX_POPULATION_SIZE) {
            Collections.shuffle(nextPop, random);
            nextPop = new ArrayList<>(nextPop.subList(0, MAX_POPULATION_SIZE));
        }

        return nextPop;
    }

    // === Soglie dipendenti dalla taglia ===
    private double sizeFactor(int size) {
        return 1.0 + 0.1 * (size - 5); // da 0.6 (size=1) a 1.5 (size=10)
    }

    private int herbSurviveThreshold(Creature h) {
        return (int)Math.round(THRESHOLD_UNIT * sizeFactor(h.getSize()));
    }
    private int herbReproThreshold(Creature h) {
        return herbSurviveThreshold(h) * 2;
    }
    private int carnSurviveThreshold(Creature k) {
        return (int)Math.round(THRESHOLD_UNIT * sizeFactor(k.getSize()));
    }
    private int carnReproThreshold(Creature k) {
        return carnSurviveThreshold(k) * 2;
    }

    // === Probabilità successo per smell ===
    private double smellSuccessProb(int effectiveSmell) {
        double effS = 1.0 - Math.exp(-effectiveSmell / 3.0);
        double base = 0.30 + 0.50 * effS;
        return Math.max(0.05, Math.min(0.90, base));
    }

    // === Tratti attivi (T4 disattivato) ===
    private int[] effectiveTripletForChecks(Creature c) {
        int smell = c.getSmell();
        int size  = c.getSize();
        int x     = c.isHerbivore() ? c.getHearing() : c.getVision();

        List<Integer> traits = c.getActiveTraits();

        if (traits.contains(1)) smell = Math.min(10, smell + 2);
        if (traits.contains(2)) size  = Math.min(10, size + 2);
        if (traits.contains(3)) { // compensator
            if (smell <= size && smell <= x) smell++;
            else if (size <= smell && size <= x) size++;
            else x++;
        }

        return new int[]{smell, size, x};
    }

    // === Foraging erbivori ===
    // registra anche i tentativi usati in attemptSink
    private int herbivoreForagingAccumulate(
            Creature herb, Ecosystem eco, int targetRepro,
            Map<Creature, Integer> attemptSink) {

        int[] eff = effectiveTripletForChecks(herb);
        int effSmell = eff[0];
        double pBase = smellSuccessProb(effSmell) * herb.smellCostPenalty();

        double plantPressure = Math.min(1.0, (eco.getPlants() + 1.0) / Math.max(1.0, eco.getPlantCapacity() * 0.25));
        pBase *= Math.max(0.25, plantPressure);

        int score = 0;
        int attempts = 0;
        int maxAttempts = 10 + (herb.hasTacticalGenius() ? 1 : 0); // cap per-erbivoro per ciclo

        while (score < targetRepro && attempts < maxAttempts) {
            if (eco.getPlants() < eco.getPlantPerHerb()) break;   // piante insufficienti
            attempts++;
            if (random.nextDouble() < pBase) {
                if (eco.consumePlantPortion()) {
                    score += HERB_SCORE_PER_FOOD;
                }
            }
        }
        
        // importante: registra i tentativi davvero usati
        attemptSink.put(herb, attempts);
        return score;
    }

    // === Scavenge carnivori ===
    private int carnivoreSingleScavengeScore(Creature carn) {
        int[] eff = effectiveTripletForChecks(carn);
        double p = smellSuccessProb(eff[0]) * carn.smellCostPenalty();
        return (random.nextDouble() < p) ? CARN_SCORE_PER_FOOD : 0;
    }

    // === Risoluzione scontro ===
    private FightResult resolveEncounter(Creature c1, Creature c2) {
        // niente scontro tra pari dieta
        if ((c1.isCarnivore() && c2.isCarnivore()) || (c1.isHerbivore() && c2.isHerbivore()))
            return new FightResult(null, null, null);

        Creature carn = c1.isCarnivore() ? c1 : c2;
        Creature herb = c1.isHerbivore() ? c1 : c2;

        // calcolo attributi effettivi (con tratti applicati)
        int[] eCarn = effectiveTripletForChecks(carn);
        int[] eHerb = effectiveTripletForChecks(herb);

        int V = eCarn[2]; // visione carnivoro
        int H = eHerb[2]; // udito erbivoro

        // REGOLA DETERMINISTICA: fuga se H - V >= 2
        if ((H - V) >= 2) {
            return new FightResult(null, herb, carn); // fuga sicura
        }

        // ALTRIMENTI: nessuna fuga, si combatte per taglia
        Creature winner = determineFightWinnerBySize(carn, herb);
        Creature loser  = (winner == carn) ? herb : carn;
        return new FightResult(winner, null, loser);
    }

    private Creature determineFightWinnerBySize(Creature carn, Creature herb) {
        int sc = effectiveTripletForChecks(carn)[1];
        int sh = effectiveTripletForChecks(herb)[1];
        if (sc == sh) return random.nextDouble() < 0.5 ? carn : herb;
        return (sc > sh) ? carn : herb;
    }

    // === Riproduzione ===
    private void applyReproduction(List<Creature> breedingPool, List<Creature> population) {
        int currentSize = population.size();
        int availableSpace = MAX_POPULATION_SIZE - currentSize;
        if (availableSpace <= 0) return;

        Collections.shuffle(breedingPool, random);
        int maxOffspring = Math.min(breedingPool.size() / 2, availableSpace / 2);

        for (int i = 0; i < breedingPool.size() - 1 && reproductionCount < maxOffspring; i += 2) {
            Creature p1 = breedingPool.get(i);
            Creature p2 = breedingPool.get(i + 1);
            Creature child = createSexualOffspring(p1, p2);
            population.add(child);
            reproductionCount++;
        }
    }

    private Creature createSexualOffspring(Creature p1, Creature p2) {
        int smell = random.nextBoolean() ? p1.getSmell() : p2.getSmell();
        int size  = random.nextBoolean() ? p1.getSize() : p2.getSize();
        boolean carn = random.nextBoolean() ? p1.getDiet() : p2.getDiet();

        int x;
        if (carn) {
            int v1 = p1.isCarnivore() ? p1.getVision() : 1;
            int v2 = p2.isCarnivore() ? p2.getVision() : 1;
            x = random.nextBoolean() ? v1 : v2;
        } else {
            int h1 = p1.isHerbivore() ? p1.getHearing() : 1;
            int h2 = p2.isHerbivore() ? p2.getHearing() : 1;
            x = random.nextBoolean() ? h1 : h2;
        }

        CreatureProblem problem = (CreatureProblem) p1.getProblem();
        List<Integer> genes = List.of(smell, size, x);
        Creature child = new Creature(problem, genes);
        child.setLongevity(0);
        child.setDiet(carn);

        int inheritedTrait = Math.max(p1.getTraitLevel(), p2.getTraitLevel());
        child.setTraitLevel(inheritedTrait);

        List<Integer> traitsPool = new ArrayList<>();
        traitsPool.addAll(p1.getActiveTraits());
        traitsPool.addAll(p2.getActiveTraits());
        Collections.shuffle(traitsPool);
        for (int i = 0; i < Math.min(3, traitsPool.size()); i++) {
            child.addTrait(traitsPool.get(i), false);
        }

        if (random.nextDouble() < 0.4) mutateCreature(child);
        child.enforceBudget();
        return child;
    }

    private void mutateCreature(Creature c) {
        if (random.nextDouble() < 0.4) c.setSmell(c.getSmell() + (random.nextBoolean() ? 1 : -1));
        if (random.nextDouble() < 0.4) c.setSize (c.getSize()  + (random.nextBoolean() ? 1 : -1));
        if (random.nextDouble() < 0.4) {
            if (c.isHerbivore()) c.setHearing(c.getHearing() + (random.nextBoolean() ? 1 : -1));
            else                 c.setVision (c.getVision()  + (random.nextBoolean() ? 1 : -1));
        }

        int tl = c.getTraitLevel();
        if (tl < 5 && random.nextDouble() < 0.4) {
            int newLevel = tl + 1;
            c.setTraitLevel(newLevel);
            c.addTrait(newLevel, false);
        }

        c.enforceBudget();
    }

    // === Stat getters ===
    public int getStarvationDeaths() { return starvationDeaths; }
    public int getEscapeCount()      { return escapeCount; }
    public int getReproductionCount(){ return reproductionCount; }
    public int getCarnivoreCount()   { return carnivoreCount; }
    public int getHerbivoreCount()   { return herbivoreCount; }

    // === Risultato combattimento ===
    private static class FightResult {
        Creature winner;
        Creature fugitive;
        Creature loser;
        FightResult(Creature winner, Creature fugitive, Creature loser) {
            this.winner = winner;
            this.fugitive = fugitive;
            this.loser = loser;
        }
    }
}
