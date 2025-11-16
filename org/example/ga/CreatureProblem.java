package org.example.ga;

import org.uma.jmetal.problem.doubleproblem.impl.AbstractDoubleProblem;
import org.uma.jmetal.solution.doublesolution.DoubleSolution;

import java.util.ArrayList;
import java.util.List;

public class CreatureProblem extends AbstractDoubleProblem {

    public CreatureProblem() {
        setNumberOfVariables(3);   // [0]=Smell, [1]=Size, [2]=XGene(H|V)
        setNumberOfObjectives(1);
        setName("CreatureEvolution");

        List<Double> lower = new ArrayList<>(3);
        List<Double> upper = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            lower.add(1.0);
            upper.add(10.0);
        }
        setVariableBounds(lower, upper);
    }

    @Override
    public void evaluate(DoubleSolution solution) {
        // Fitness di comodo: favorisce combinazioni alte ma ignora vincolo dieta
        // (la simulazione reale usa gli operatori, qui mettiamo un placeholder coerente)
        double smell = solution.getVariable(0);
        double size  = solution.getVariable(1);
        double xgene = solution.getVariable(2);
        double fitness = (smell * 0.5 + xgene * 0.5) * size; // ponderazione bilanciata
        solution.setObjective(0, -fitness); // jMetal minimizza
    }
}
