package org.example.geneticAlgorithm.operators;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.geneticAlgorithm.GeneticAlgorithm;
import org.example.models.Chromosome;
import org.example.models.EncodedExam;
import org.example.models.Timeslot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class Mutation {
    /*
     * Calculate avg fitness value of population
     * calculate fitness value for each chromosome
     * threshold = avg fitness value
     * if fitness value of a chromosome < threshold, set high mutation rate
     * if fitness value of a chromosome >=, set low mutation rate
     */
    private static final Logger logger = LogManager.getLogger(GeneticAlgorithm.class);
    private Map<Chromosome, Double> mutationRates = new ConcurrentHashMap<>();
    private Random random = new Random();

    private final double lowMutationRate = 0.005;
    private final double highMutationRate = 0.07;
    int randomExam1;
    int randomExam2;

    public void mutation(HashMap<Chromosome, Double> fitnessScores, ArrayList<Chromosome> population) {

        double threshHold = calculateAvgFitnessScore(fitnessScores);
        setMutationRates(fitnessScores, threshHold);
        mutationRates.forEach((key, value) -> {
            double randomProbability = random.nextDouble() * 0.1;

            if (randomProbability <= value) {
                swapMutation(key);
            }
        });
    }


    public void swapMutation(Chromosome chromosome) {
        randomExam1 = random.nextInt(chromosome.getEncodedExams().size());
        do {
            randomExam2 = random.nextInt(chromosome.getEncodedExams().size());
        } while (randomExam1 == randomExam2);

        EncodedExam firstExam = chromosome.getEncodedExams().get(randomExam1);
        EncodedExam secondExam = chromosome.getEncodedExams().get(randomExam2);

        Timeslot tempTimeSlot = firstExam.getTimeSlot();
        firstExam.setTimeSlot(secondExam.getTimeSlot());
        secondExam.setTimeSlot(tempTimeSlot);

        String tempClassCode = firstExam.getClassroomCode();
        firstExam.setClassroomCode(secondExam.getClassroomCode());
        secondExam.setClassroomCode(tempClassCode);
    }

    private void setMutationRates(HashMap<Chromosome, Double> fitnessScores, double threshHold) {
        fitnessScores.entrySet().forEach(entry -> {
            if (entry.getValue() < threshHold) {
                mutationRates.put(entry.getKey(), highMutationRate);
            } else {
                mutationRates.put(entry.getKey(), lowMutationRate);
            }
        });
    }

    private double calculateAvgFitnessScore(HashMap<Chromosome, Double> fitnessScores) {
        double totalFitnessScore = 0;

        for (Double value : fitnessScores.values()) {
            totalFitnessScore += value;
        }
        return totalFitnessScore / fitnessScores.size();
    }
}
