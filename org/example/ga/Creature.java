package org.example.ga;

import org.uma.jmetal.solution.doublesolution.DoubleSolution;
import org.uma.jmetal.problem.doubleproblem.DoubleProblem;
import java.util.*;

public class Creature implements DoubleSolution {

    private DoubleProblem problem;
    private List<Double> variables;
    private List<Double> objectives;
    private int longevity;
    private boolean alive;
    private String id;
    // BUDGET: Size + Smell + Hearing|Vision
    private static final int MAX_TOTAL_STATS = 20;
    private static final int MAX_LONGEVITY = 20;
    private static final Random random = new Random();
    private Map<Object, Object> attributes;
    private static long idCounter = 0;

    // Dieta & tratti
    private boolean carnivore;
    private int traitLevel;
    // stealth disattivato nel nuovo modello, campo lasciato per compatibilità
    private boolean stealthActive;
    private List<Integer> activeTraits;
    private boolean printTraitMessages;

    // === Indici variabili ===
    // [0]=Smell, [1]=Size, [2]=XGene(Hearing o Vision)
    private static final int IDX_SMELL = 0;
    private static final int IDX_SIZE  = 1;
    private static final int IDX_XGENE = 2;

    public Creature(DoubleProblem problem, List<Integer> initialGenes) {
        this.problem = problem;
        this.variables = new ArrayList<>();
        this.objectives = new ArrayList<>();
        this.attributes = new HashMap<>();
        this.carnivore = random.nextBoolean();
        this.traitLevel = 0;
        this.stealthActive = false;
        this.activeTraits = new ArrayList<>();
        this.printTraitMessages = false;

        for (int i = 0; i < problem.getNumberOfObjectives(); i++) {
            objectives.add(0.0);
        }

        this.longevity = 0;
        this.alive = true;
        this.id = generateId();

        // Inizializza 3 variabili [Smell, Size, XGene]
        int smell = 5, size = 5, xgene = 5;
        if (initialGenes != null && (initialGenes.size() == 2 || initialGenes.size() == 3)) {
            smell = clampGene(initialGenes.get(0));
            size  = clampGene(initialGenes.get(1));
            if (initialGenes.size() == 3) xgene = clampGene(initialGenes.get(2));
        }
        setGenesWithConstraint(smell, size, xgene);
    }

    public Creature(Creature original) {
        this.problem = original.problem;
        this.variables = new ArrayList<>(original.variables);
        this.objectives = new ArrayList<>(original.objectives);
        this.attributes = new HashMap<>(original.attributes);
        this.carnivore = original.carnivore;
        this.traitLevel = original.traitLevel;
        this.stealthActive = false; // disattivato nel nuovo modello
        this.activeTraits = new ArrayList<>(original.activeTraits);
        this.printTraitMessages = false;
        this.longevity = 0;
        this.alive = true;
        this.id = generateId();
        enforceBudget();
    }

    // === Diet helpers ===
    public DoubleProblem getProblem() { return problem; }

    public boolean isCarnivore() { return carnivore; }
    public boolean isHerbivore() { return !carnivore; }
    public void setDiet(boolean carnivore) {
        this.carnivore = carnivore;
        enforceBudget();
    }
    public boolean getDiet() { return carnivore; }

    // === TRATTI: mapping nuovo ===
    // 1: Perception boost (+2 Smell effettivo)
    // 2: Size boost (+2 Size effettiva)
    // 3: Compensator (+1 alla statistica più bassa tra Smell/Size/XGene effettivi)
    // 4: Tactical Genius (+1 food check) — usato negli operatori
    // 5: NESSUN EFFETTO (stealth rimosso)

    public boolean hasPerceptionBoostTrait() { return activeTraits.contains(1); }
    public boolean hasSizeBoostTrait()       { return activeTraits.contains(2); }
    public boolean hasCompensatorTrait()     { return activeTraits.contains(3); }
    public boolean hasTacticalGenius()       { return activeTraits.contains(4); }

    // Compat: manteniamo le vecchie firme usate esternamente
    public boolean hasTacticalGeniusTrait()  { return hasTacticalGenius(); }
    public boolean canUseDoubleTrait()       { return hasTacticalGenius(); } // alias legacy
    public boolean canUseDevourerTrait()     { return false; } // trait 3 non è più Devourer
    public boolean hasStealthTrait()         { return false; } // disattivato

    public void addTrait(int traitNumber) { addTrait(traitNumber, false); }

    public void addTrait(int traitNumber, boolean printMessage) {
        if (traitNumber < 1 || traitNumber > 5) return;
        if (activeTraits.contains(traitNumber)) return;
        if (activeTraits.size() >= 3) {
            activeTraits.remove(0);
        }
        activeTraits.add(traitNumber);
        // trait 5: nessun effetto
    }

    public List<Integer> getActiveTraits() { return new ArrayList<>(activeTraits); }

    public int getTraitLevel() { return traitLevel; }
    public void setTraitLevel(int level) { this.traitLevel = Math.min(5, Math.max(0, level)); }

    public void setPrintTraitMessages(boolean print) { this.printTraitMessages = print; }

    public boolean isAlive() { return alive; }
    public void setAlive(boolean alive) { this.alive = alive; }
    public void dieOfOldAge() { if (this.alive) this.alive = false; }
    public void kill() { this.alive = false; }
    public String getId() { return id; }

    // === Budget / variabili ===
    private int clampGene(int v) { return Math.max(1, Math.min(10, v)); }

    private void setGenesWithConstraint(int smell, int size, int xgene) {
        smell = clampGene(smell);
        size  = clampGene(size);
        xgene = clampGene(xgene);

        int total = smell + size + xgene;
        int over = total - MAX_TOTAL_STATS;
        if (over > 0) {
            int before = xgene; xgene = Math.max(1, xgene - over); over -= (before - xgene);
        }
        if (over > 0) {
            int before = smell; smell = Math.max(1, smell - over); over -= (before - smell);
        }
        if (over > 0) {
            int before = size; size = Math.max(1, size - over); over -= (before - size);
        }

        ensureVarSize(3);
        variables.set(IDX_SMELL, (double) smell);
        variables.set(IDX_SIZE,  (double) size);
        variables.set(IDX_XGENE, (double) xgene);
    }

    public void enforceBudget() {
        int smell = getSmell();
        int size  = getSize();
        int xgene = getXGene();
        setGenesWithConstraint(smell, size, xgene);
    }

    // === Geni grezzi ===
    public int getSmell() { return (int) Math.round(getVariable(IDX_SMELL)); }
    public void setSmell(int v) { setVariable(IDX_SMELL, (double) clampGene(v)); }

    public int getHearing() { return isHerbivore() ? getXGene() : 1; }
    public void setHearing(int v) { if (isHerbivore()) setXGene(v); }

    public int getVision() { return isCarnivore() ? getXGene() : 1; }
    public void setVision(int v) { if (isCarnivore()) setXGene(v); }

    private int getXGene() { return (int) Math.round(getVariable(IDX_XGENE)); }
    private void setXGene(int v) { setVariable(IDX_XGENE, (double) clampGene(v)); }

    public int getSize() { return (int) Math.round(getVariable(IDX_SIZE)); }
    public void setSize(int v) { setVariable(IDX_SIZE, (double) clampGene(v)); }

    // === STAT EFFETTIVE (per operatori) ===
    /** Smell effettivo: applica trait 1 (+2) */
    public int getEffectiveSmell() {
        int s = getSmell();
        if (hasPerceptionBoostTrait()) s += 2;
        return s;
    }

    /** Size effettiva: applica trait 2 (+2) */
    public int getEffectiveSize() {
        int z = getSize();
        if (hasSizeBoostTrait()) z += 2;
        return z;
    }

    /** Hearing effettivo (solo erbivori) */
    public int getEffectiveHearing() {
        return isHerbivore() ? getHearing() : 1;
    }

    /** Vision effettiva (solo carnivori) */
    public int getEffectiveVision() {
        return isCarnivore() ? getVision() : 1;
    }

    /**
     * Tripla effettiva [Smell, Size, XGene] con il tratto 3 (Compensator):
     * +1 alla più bassa tra le tre statistiche effettive.
     */
    public int[] getEffectiveTripletWithCompensator() {
        int effSmell = getEffectiveSmell();
        int effSize  = getEffectiveSize();
        int effX     = isHerbivore() ? getEffectiveHearing() : getEffectiveVision();

        if (hasCompensatorTrait()) {
            if (effSmell <= effSize && effSmell <= effX) effSmell++;
            else if (effSize <= effSmell && effSize <= effX) effSize++;
            else effX++;
        }
        return new int[]{ effSmell, effSize, effX };
    }

    // Compat: "Perception" ora = Smell effettivo (mantiene vecchie chiamate funzionanti)
    public int getPerception() { return getEffectiveSmell(); }

    // Costo metabolico (opzionale)
    public double smellCostPenalty() {
        int excess = Math.max(0, getSmell() - 8); // penalizza 9-10 (gene grezzo)
        return 1.0 - 0.04 * excess;              // 0.92..1.0
    }

    public int getLongevity() { return longevity; }
    public void setLongevity(int longevity) { this.longevity = longevity; }

    public boolean incrementLongevity() {
        this.longevity++;
        if (this.longevity >= MAX_LONGEVITY) {
            dieOfOldAge();
            return false;
        }
        return true;
    }

    private String generateId() {
        return "CR_" + (++idCounter) + "_" + random.nextInt(1000);
    }

    public boolean satisfiesConstraint() {
        int smell = getSmell();
        int size  = getSize();
        int xgene = getXGene();
        return (smell + size + xgene) <= MAX_TOTAL_STATS;
    }

    public double calculateFitness() {
        // placeholder semplice
        return longevity * (1 + getSmell() / 10.0);
    }

    @Override
    public String toString() {
        String diet = carnivore ? "Carn" : "Herb";
        String hv = isCarnivore() ? ("V:" + getVision()) : ("H:" + getHearing());
        String traitInfo = activeTraits.isEmpty() ? "" : " Tratti:" + activeTraits;
        return String.format("Creature[ID: %s, %s, Sml: %d, %s, Size: %d, Long: %d%s]",
                id, diet, getSmell(), hv, getSize(), longevity, traitInfo);
    }

    // === DoubleSolution interface ===
    @Override
    public Double getVariable(int index) { ensureVarSize(index + 1); return variables.get(index); }

    @Override
    public void setVariable(int index, Double value) {
        ensureVarSize(index + 1);
        int intValue = clampGene(value.intValue());
        variables.set(index, (double) intValue);

        if (variables.size() >= 3) {
            int smell = (int) Math.round(variables.get(IDX_SMELL));
            int size  = (int) Math.round(variables.get(IDX_SIZE));
            int xgene = (int) Math.round(variables.get(IDX_XGENE));
            setGenesWithConstraint(smell, size, xgene);
        }
    }

    private void ensureVarSize(int n) {
        while (variables.size() < n) variables.add(1.0);
    }

    @Override
    public List<Double> getVariables() { return variables; }

    @Override
    public Double getObjective(int index) { return objectives.get(index); }

    @Override
    public void setObjective(int index, Double value) {
        if (objectives.size() <= index) {
            for (int i = objectives.size(); i <= index; i++) {
                objectives.add(0.0);
            }
        }
        objectives.set(index, value);
    }

    @Override
    public List<Double> getObjectives() { return objectives; }

    @Override
    public DoubleSolution copy() { return new Creature(this); }

    @Override
    public Map<Object, Object> getAttributes() { return attributes; }

    @Override
    public int getNumberOfVariables() { return variables.size(); }

    @Override
    public int getNumberOfObjectives() { return objectives.size(); }

    @Override
    public Double getLowerBound(int index) { return problem.getLowerBound(index); }

    @Override
    public Double getUpperBound(int index) { return problem.getUpperBound(index); }
}
