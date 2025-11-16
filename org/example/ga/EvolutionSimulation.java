package org.example.ga;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class EvolutionSimulation {
    public static void main(String[] args) {
        CreatureProblem problem = new CreatureProblem();
        Ecosystem ecosystem = new Ecosystem();
        ecosystem.initializePopulation(1000, problem, 1, 4);   // 1 carnivoro : 4 erbivori

        // Configura capacità portante delle piante (puoi tarare questi valori)
        // startStock=8000, capacity(K)=12000, logistic=0.40, plantPerHerb=1, seasonal=true
        ecosystem.configurePlants(8000, 12000, 0.40, 1, true);

        ecosystem.setVerboseMode(false);
        System.out.println("=== INIZIO SIMULAZIONE EVOLUTIVA ===");
        ecosystem.printPopulationStats();

        for (int cycle = 0; cycle < 40; cycle++) {
            System.out.printf("%n--- Ciclo %d ---%n", cycle + 1);
            ecosystem.runLifeCycle();
            ecosystem.printPopulationStats();

            if (ecosystem.getAliveCount() == 0) {
                System.out.println("La popolazione si è estinta!");
                break;
            }
        }

        System.out.println("\n=== FINE SIMULAZIONE ===");
        printTopLongevityCreatures(ecosystem);
        printTraitAnalysis(ecosystem);
    }

    private static void printTopLongevityCreatures(Ecosystem ecosystem) {
        List<Creature> aliveCreatures = ecosystem.getAliveCreatures();

        if (aliveCreatures.isEmpty()) {
            System.out.println("Nessuna creatura sopravvissuta!");
            return;
        }

        List<Creature> topCreatures = aliveCreatures.stream()
                .sorted(Comparator.comparingInt(Creature::getLongevity).reversed())
                .limit(3)
                .collect(Collectors.toList());

        System.out.println("\n🏆 TOP 3 CREATURE PER LONGEVITÀ:");
        System.out.println("=================================");
        for (int i = 0; i < topCreatures.size(); i++) {
            Creature creature = topCreatures.get(i);
            System.out.printf("%d° - %s%n", i + 1, creature);
        }
    }

    private static void printTraitAnalysis(Ecosystem ecosystem) {
        List<Creature> aliveCreatures = ecosystem.getAliveCreatures();

        System.out.println("\n📈 ANALISI TRATTI DELLA POPOLAZIONE:");
        System.out.println("====================================");

        if (aliveCreatures.isEmpty()) {
            System.out.println("Nessuna creatura da analizzare.");
            return;
        }

        long carnivores = aliveCreatures.stream().filter(Creature::isCarnivore).count();
        long herbivores = aliveCreatures.stream().filter(Creature::isHerbivore).count();

        System.out.printf("Carnivori: %d (%d%%) | Erbivori: %d (%d%%)%n",
                carnivores, (carnivores * 100 / aliveCreatures.size()),
                herbivores, (herbivores * 100 / aliveCreatures.size()));

        // Medie specifiche
        double avgLongevity = ecosystem.getAverageLongevity();
        double avgSmell     = ecosystem.getAveragePerception(); // alias: Smell
        double avgSize      = ecosystem.getAverageSize();

        double avgHearing = aliveCreatures.stream()
                .filter(Creature::isHerbivore)
                .mapToInt(Creature::getHearing)
                .average().orElse(0);

        double avgVision = aliveCreatures.stream()
                .filter(Creature::isCarnivore)
                .mapToInt(Creature::getVision)
                .average().orElse(0);

        System.out.println("\n📊 STATISTICHE FINALI:");
        System.out.printf("Creature sopravvissute: %d%n", aliveCreatures.size());
        System.out.printf("Longevità media: %.1f cicli%n", avgLongevity);
        System.out.printf("Smell (olfatto) medio: %.1f%n", avgSmell);
        System.out.printf("Hearing medio (solo erbivori): %s%n",
                (herbivores > 0 ? String.format("%.1f", avgHearing) : "n/a"));
        System.out.printf("Vision medio (solo carnivori): %s%n",
                (carnivores > 0 ? String.format("%.1f", avgVision) : "n/a"));
        System.out.printf("Taglia (Size) media: %.1f%n", avgSize);
        System.out.printf("Somma media Size+Smell+(H|V): %.1f%n", ecosystem.getAverageTotalStats());

        // Miglior “stats” = Size + Smell + (H|V)
        Creature bestStats = aliveCreatures.stream()
                .max(Comparator.comparingInt(c -> c.getSize() + c.getSmell() + (c.isHerbivore() ? c.getHearing() : c.getVision())))
                .orElse(null);

        if (bestStats != null) {
            boolean isCarn = bestStats.isCarnivore();
            int sml = bestStats.getSmell();
            int sz  = bestStats.getSize();
            int x   = isCarn ? bestStats.getVision() : bestStats.getHearing();
            int sum = sml + sz + x;
            String dietType = isCarn ? "Carnivoro" : "Erbivoro";
            String xLabel   = isCarn ? "V" : "H";

            System.out.printf("%n🔥 Miglior stats (Sml:%d + Size:%d + %s:%d = %d, %s): %s%n",
                    sml, sz, xLabel, x, sum, dietType, bestStats);
        }
    }
}
