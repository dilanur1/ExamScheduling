package org.example.geneticAlgorithm;

import lombok.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.dataPreprocessing.RandomDataGenerator;
import org.example.geneticAlgorithm.operators.*;
import org.example.models.*;
import org.example.utils.*;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

import static org.example.utils.DataStructureHelper.sortByValueDescending;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Data
public class GeneticAlgorithm {


    private ArrayList<Course> courses = new ArrayList<>();
    private ArrayList<Invigilator> invigilators = new ArrayList<>();
    private ArrayList<Classroom> classrooms = new ArrayList<>();
    private ArrayList<Student> students = new ArrayList<>();
    private ArrayList<Timeslot> timeslots = new ArrayList<>();
    private ArrayList<Exam> exams = new ArrayList<>();
    private ArrayList<EncodedExam> encodedExams = new ArrayList<>();
    private Chromosome chromosome;
    private HashMap<String, ArrayList<?>> chromosomeForVisualization = new HashMap<>();
    private ArrayList<Chromosome> population = new ArrayList<>();
    private ArrayList<HashMap<String, ArrayList<?>>> populationForVisualization = new ArrayList<>();
    private Schedule schedule;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private int interval;

    private HashMap<Chromosome, Double> hardConstraintFitnessScores = new HashMap<>();
    private HashMap<Chromosome, Double> softConstraintFitnessScores = new HashMap<>();
    private HashMap<Chromosome, Double> fitnessScores = new HashMap<>();
    private static final Logger logger = LogManager.getLogger(GeneticAlgorithm.class);
    private ArrayList<Chromosome> parents = new ArrayList<>();
    private double bestFitnessScore;
    private double lastBestFitnessScore;
    private int populationSize = Integer.parseInt(ConfigHelper.getProperty("POPULATION_SIZE"));
    private ArrayList<EncodedExam> encodedExamArrayList = new ArrayList<>();
    private long chromosomeIdCounter = 0;
    private int maxGeneration = Integer.parseInt(ConfigHelper.getProperty("MAX_GENERATIONS"));
    private double lowMutationRate = Double.parseDouble(ConfigHelper.getProperty("LOW_MUTATION_RATE"));
    private double highMutationRate = Double.parseDouble(ConfigHelper.getProperty("HIGH_MUTATION_RATE"));
    private double crossoverRate = Double.parseDouble(ConfigHelper.getProperty("CROSSOVER_RATE"));
    private boolean isStable = false;


    public void generateData() {
        HashMap<String, HashMap<String, ArrayList<Object>>> randomData = RandomDataGenerator.combineAllData();
        this.courses = RandomDataGenerator.generateCourseInstances(randomData.get("courseData"));
        //this.courses = new ArrayList<>(courses.subList(0, Math.min(Integer.parseInt(ConfigHelper.getProperty("COURSE_COUNT")), courses.size())));

        this.invigilators = RandomDataGenerator.generateInvigilatorInstances(randomData.get("invigilatorData"));
        this.invigilators = new ArrayList<>(invigilators.subList(0, Math.min(Integer.parseInt(ConfigHelper.getProperty("INVIGILATOR_COUNT")), invigilators.size())));

        this.classrooms = RandomDataGenerator.generateClassroomInstances(randomData.get("classroomData"));
        //this.classrooms = new ArrayList<>(classrooms.subList(0, Math.min(Integer.parseInt(ConfigHelper.getProperty("CLASSROOM_COUNT")), classrooms.size())));

        this.students = RandomDataGenerator.generateStudentInstances(randomData.get("studentData"));
        this.students = new ArrayList<>(students.subList(0, Math.min(Integer.parseInt(ConfigHelper.getProperty("STUDENT_COUNT")), students.size())));

        this.startDate = LocalDate.parse(ConfigHelper.getProperty("START_DATE"));
        this.endDate = LocalDate.parse(ConfigHelper.getProperty("END_DATE")); // this date is not included
        this.startTime = LocalTime.parse(ConfigHelper.getProperty("START_TIME"));
        this.endTime = LocalTime.parse(ConfigHelper.getProperty("END_TIME"));
        this.interval = Integer.parseInt(ConfigHelper.getProperty("TIME_SLOT_INTERVAL"));
        this.schedule = RandomDataGenerator.generateSchedule(startDate, endDate, startTime, endTime, interval);
        this.timeslots = schedule.calculateTimeSlots();

        logger.debug("Number of Students: " + students.size());
        logger.debug("Number of Classroom: " + classrooms.size());
        logger.debug("Number of invigilators: " + invigilators.size());
        logger.debug("Number of courses: " + courses.size());
        logger.debug("Number of timeslots: " + schedule.calculateMaxTimeSlots());

        HashMap<String, ArrayList<?>> resultCoursesStudents = Initialization.heuristicMapCoursesWithStudents(this.courses, this.students);
        this.courses = DataStructureHelper.castArrayList(resultCoursesStudents.get("courses"), Course.class);
        this.students = DataStructureHelper.castArrayList(resultCoursesStudents.get("students"), Student.class);
        logger.debug("heuristicMapCoursesWithStudents finished.");

        File holidaysFile = new File(FileHelper.holidayFilePath);
        if (!holidaysFile.exists()) {
            FileHelper.saveHolidaysToFile();
        }

    }

    public ArrayList<Chromosome> initializationAndEncode() {
        int populationSize = Integer.parseInt(ConfigHelper.getProperty("POPULATION_SIZE"));
        for (int i = 0; i < populationSize; i++) {

            logger.debug("Population " + i);

            HashMap<String, ArrayList<?>> resultExams = Initialization.createExamInstances(this.courses);
            this.exams = DataStructureHelper.castArrayList(resultExams.get("exams"), Exam.class);
            logger.debug("createExamInstances finished.");
            Random rand = new Random();

            Collections.shuffle(this.exams, new Random(rand.nextInt(10000)));
            Collections.shuffle(this.invigilators, new Random(rand.nextInt(10000)));
            Collections.shuffle(this.classrooms, new Random(rand.nextInt(10000)));

            HashMap<String, ArrayList<?>> resultCoursesInvigilators = Initialization.heuristicMapExamsWithInvigilators(exams, invigilators);
            //HashMap<String, ArrayList<?>> resultCoursesInvigilators = Initialization.randomMapExamsWithInvigilators(exams, invigilators);
            this.exams = DataStructureHelper.castArrayList(resultCoursesInvigilators.get("exams"), Exam.class);
            logger.debug("heuristicMapExamsWithInvigilators finished.");

            Collections.shuffle(exams, new Random(rand.nextInt(10000)));
            HashMap<String, ArrayList<?>> resultCoursesClassrooms = Initialization.heuristicMapExamsWithClassrooms(exams, classrooms);
            //HashMap<String, ArrayList<?>> resultCoursesClassrooms = Initialization.randomMapExamsWithClassrooms(exams, classrooms);
            this.exams = DataStructureHelper.castArrayList(resultCoursesClassrooms.get("exams"), Exam.class);
            logger.debug("heuristicMapExamsWithClassrooms finished.");

            Collections.shuffle(exams, new Random(rand.nextInt(10000)));
            HashMap<String, ArrayList<?>> resultCoursesTimeslots = Initialization.heuristicMapExamsWithTimeslots(exams, timeslots);
            //HashMap<String, ArrayList<?>> resultCoursesTimeslots = Initialization.randomMapExamsWithTimeslots(exams, timeslots);
            this.exams = DataStructureHelper.castArrayList(resultCoursesTimeslots.get("exams"), Exam.class);
            logger.debug("heuristicMapExamsWithTimeslots finished.");

            encode();

            this.chromosomeForVisualization.put("exams", new ArrayList<>(exams));
            this.chromosomeForVisualization.put("invigilators", new ArrayList<>(invigilators));
            this.chromosomeForVisualization.put("classrooms", new ArrayList<>(classrooms));
            this.populationForVisualization.add(new HashMap<>(chromosomeForVisualization));
            reset();
        }
        VisualizationHelper.generateReports(courses, students, classrooms);
        return population;
    }

    public void encode() {
        Encode encode = new Encode();
        this.encodedExams = encode.encode(this.exams, this.classrooms);

        chromosome = new Chromosome(chromosomeIdCounter, encodedExams, 0);
        chromosomeIdCounter++;
        this.population.add(chromosome);
        logger.debug("Encode is finished.");
    }

    public void visualization(int wantedExamScheduleCount, int currentGeneration) {

        String baseFileName = "graphs/Population" + currentGeneration + "/";
        String bestPath = baseFileName + "best/";
        String randomPath = baseFileName + "random/";
        FileHelper.createDirectory(baseFileName);
        FileHelper.createDirectory(bestPath);
        FileHelper.createDirectory(randomPath);

        // for best chromosomes
        Set<Chromosome> bestChromosomes = new HashSet<>();
        for (Map.Entry<Chromosome, Double> entry : fitnessScores.entrySet()) {
            if (bestChromosomes.size() == 3) {
                break;
            }
            bestChromosomes.add(entry.getKey());
        }

        // for random chromosomes
        Set<Integer> uniqueNumbers = new HashSet<>();
        Random rand = new Random();
        while (uniqueNumbers.size() < wantedExamScheduleCount) {
            uniqueNumbers.add(rand.nextInt(populationForVisualization.size()));
        }


        for (int k = 0; k < wantedExamScheduleCount; k++) {

            // Exam Schedule :
            // this will visualize a random exam schedule from population

            // this exam schedule is for invigilators not for students
            Chromosome bestChromosome = (Chromosome) bestChromosomes.toArray()[k];
            ArrayList<EncodedExam> bestExamScheduleForInvigilators = bestChromosome.getEncodedExams();
            HTMLHelper.generateExamTable(startTime, endTime, startDate, endDate, interval, bestExamScheduleForInvigilators, bestPath + bestChromosome.getFitnessScore() + "_Best Exam Schedule-" + bestChromosome.getChromosomeId() + " for Invigilators.html");

            ArrayList<EncodedExam> bestExamScheduleForStudents = new ArrayList<>();
            for (EncodedExam encodedExam : bestExamScheduleForInvigilators) {
                Course course = Course.findByCourseCode(courses, encodedExam.getCourseCode());
                if (course != null) {
                    int beforeExam = course.getBeforeExamPrepTime();
                    int afterExam = course.getAfterExamPrepTime();
                    Timeslot combinedTimeslot = encodedExam.getTimeSlot();
                    Timeslot examTimeslot = new Timeslot(combinedTimeslot.getStart().plusHours(beforeExam), combinedTimeslot.getEnd().minusHours(afterExam));
                    bestExamScheduleForStudents.add(new EncodedExam(encodedExam.getCourseCode(),
                            encodedExam.getClassroomCode(),
                            examTimeslot,
                            encodedExam.getInvigilators()));
                }
            }
            HTMLHelper.generateExamTable(startTime, endTime, startDate, endDate, interval, bestExamScheduleForStudents, bestPath + bestChromosome.getFitnessScore() + "_Best Exam Schedule-" + bestChromosome.getChromosomeId() + " for Students.html");
            HTMLHelper.generateExamTableDila(startDate, endDate, bestExamScheduleForStudents, bestPath + bestChromosome.getFitnessScore() + "_Best Exam ScheduleDila-" + bestChromosome.getChromosomeId() + " for Students.html");


            // Reports that are changing : invigilators, classrooms, exam schedules
            int n = (Integer) uniqueNumbers.toArray()[k];
            HashMap<String, ArrayList<?>> randomInfo = populationForVisualization.get(n);
            Encode encode = new Encode();
            ArrayList<EncodedExam> randomExamScheduleForInvigilators = encode.encode(DataStructureHelper.castArrayList(randomInfo.get("exams"), Exam.class), this.classrooms);

            ArrayList<EncodedExam> randomExamScheduleForStudents = new ArrayList<>();
            for (EncodedExam encodedExam : randomExamScheduleForInvigilators) {
                Course course = Course.findByCourseCode(courses, encodedExam.getCourseCode());
                if (course != null) {
                    int beforeExam = course.getBeforeExamPrepTime();
                    int afterExam = course.getAfterExamPrepTime();
                    Timeslot combinedTimeslot = encodedExam.getTimeSlot();
                    Timeslot examTimeslot = new Timeslot(combinedTimeslot.getStart().plusHours(beforeExam), combinedTimeslot.getEnd().minusHours(afterExam));
                    randomExamScheduleForStudents.add(new EncodedExam(encodedExam.getCourseCode(),
                            encodedExam.getClassroomCode(),
                            examTimeslot,
                            encodedExam.getInvigilators()));
                }
            }

            HTMLHelper.generateExamTable(startTime, endTime, startDate, endDate, interval, randomExamScheduleForInvigilators, randomPath + "Random Exam Schedule-" + n + " for Invigilators.html");
            HTMLHelper.generateExamTable(startTime, endTime, startDate, endDate, interval, randomExamScheduleForStudents, randomPath + "Random Exam Schedule-" + n + " for Students.html");
            HTMLHelper.generateExamTableDila(startDate, endDate, randomExamScheduleForStudents, randomPath + "Random Exam ScheduleDila-" + n + " for Students.html");
            HTMLHelper.generateInvigilatorReport(DataStructureHelper.castArrayList(randomInfo.get("invigilators"), Invigilator.class), randomPath + "random_invigilator_report_" + n + ".html", "Invigilator Report");
            HTMLHelper.generateClassroomReport(DataStructureHelper.castArrayList(randomInfo.get("classrooms"), Classroom.class), randomPath + "random_classroom_report_" + n + ".html", "Classroom Report");
            HTMLHelper.generateExamReport(DataStructureHelper.castArrayList(randomInfo.get("exams"), Exam.class), randomPath + "random_exams_" + n + ".html", "Exam Schedule");
        }

    }

    public void reset() {
        ArrayList<Invigilator> resetInvigilators = new ArrayList<>();
        ArrayList<Classroom> resetClassrooms = new ArrayList<>();
        for (Invigilator originalInvigilator : this.invigilators) {
            Invigilator invigilator = new Invigilator(originalInvigilator.getID(), originalInvigilator.getName(), originalInvigilator.getSurname(), originalInvigilator.getMaxCoursesMonitoredCount());
            resetInvigilators.add(invigilator);
        }

        for (Classroom originalClassroom : this.classrooms) {
            Classroom classroom = new Classroom(originalClassroom.getClassroomCode(), originalClassroom.getClassroomName(), originalClassroom.getCapacity(), originalClassroom.isPcLab(), originalClassroom.getClassroomProperties());
            resetClassrooms.add(classroom);
        }
        this.invigilators = resetInvigilators;
        this.classrooms = resetClassrooms;
    }

    public void calculateFitness(boolean saveToExcel, boolean experiment, int experimentId, int currentGeneration) {
        // make a hashmap with encoded exam as a key
        // and fitness score as a value
        Fitness fitness = new Fitness(courses, students, classrooms, invigilators, startDate, endDate, startTime, endTime);
        ArrayList<double[]> hardConstraintScoresList = new ArrayList<>();
        ArrayList<double[]> softConstraintScoresList = new ArrayList<>();
        ArrayList<double[]> fitnessScoresList = new ArrayList<>();

        hardConstraintFitnessScores.clear();
        softConstraintFitnessScores.clear();
        fitnessScores.clear();

        for (Chromosome chromosome : population) {
            double[][] calculatedScores = fitness.fitnessScore(chromosome);

            double[] hardConstraintScores = calculatedScores[0];
            double[] softConstraintScores = calculatedScores[1];
            double[] scores = calculatedScores[2];

            hardConstraintScoresList.add(hardConstraintScores);
            softConstraintScoresList.add(softConstraintScores);
            fitnessScoresList.add(scores);

            double hardFitnessScore = hardConstraintScores[hardConstraintScores.length - 1];
            hardConstraintFitnessScores.put(chromosome, hardFitnessScore);

            double softFitnessScore = softConstraintScores[softConstraintScores.length - 1];
            softConstraintFitnessScores.put(chromosome, softFitnessScore);

            double fitnessScore = scores[scores.length - 1];
            fitnessScores.put(chromosome, fitnessScore);
            chromosome.setFitnessScore(fitnessScore);

        }

        // fitness sharing

        if (Boolean.parseBoolean(ConfigHelper.getProperty("FITNESS_SHARE"))) {
            Fitness.fitnessShare(population);
            // update fitnessScores and fitnessScoresList after fitness share
            // ArrayList<double[]> fitnessScoresList : chromosom id , fitness score

            for (Chromosome chromosom : population) {
                double id = chromosom.getChromosomeId();
                double fit = chromosom.getFitnessScore();

                for (Chromosome chromosom1 : fitnessScores.keySet()) {
                    if (chromosom1.getChromosomeId() == id) {
                        fitnessScores.put(chromosom1, fit);
                    }
                }

                for (double[] fScores : fitnessScoresList) {
                    if (fScores[0] == id) {
                        fScores[1] = fit;
                    }
                }
            }
        }


        hardConstraintFitnessScores = sortByValueDescending(hardConstraintFitnessScores);
        softConstraintFitnessScores = sortByValueDescending(softConstraintFitnessScores);
        fitnessScores = sortByValueDescending(fitnessScores);

        for (Chromosome chromosome : fitnessScores.keySet()) {
            logger.debug("Id of Exam Schedule: " + chromosome.getChromosomeId() + ", Score: " + fitnessScores.get(chromosome));
        }

        if (saveToExcel) {
            String baseFileName;
            if (experiment) {
                baseFileName = "experiments/experiment_" + experimentId + "/FitnessScores/";
            } else {
                baseFileName = "graphs/FitnessScores/";
            }
            FileHelper.createDirectory(baseFileName);
            FileHelper.writeHardFitnessScoresToFile(hardConstraintScoresList, baseFileName + "fitness_scores_HARD.csv");
            FileHelper.writeSoftFitnessScoresToFile(softConstraintScoresList, baseFileName + "fitness_scores_SOFT.csv");
            FileHelper.writeFitnessScoresToFile(fitnessScoresList, baseFileName + "fitness_scores.csv");
        }

    }
    public double findBestFitnessScore() {
        population.sort(Chromosome.sortChromosomesByFitnessScoreDescendingOrder);
        return population.get(0).getFitnessScore();
    }

    public void selectParents(int currentGeneration) {
        Selection selection = new Selection();
        if (currentGeneration >= maxGeneration * 0.7) {
            parents = selection.rankSelection(population);
        } else if (isStable) {
            parents = selection.rouletteWheelSelection(population);
        } else {
            parents = selection.tournamentSelection(population);
        }

    }

    public ArrayList<Chromosome> crossover() {
        Crossover crossover = new Crossover();
        ArrayList<Chromosome> childChromosomes;

        if (isStable) {
            childChromosomes = crossover.onePointCrossover(parents, chromosomeIdCounter, crossoverRate);
        } else {
            childChromosomes = crossover.twoPointCrossover(parents, chromosomeIdCounter, crossoverRate);
        }

        chromosomeIdCounter = childChromosomes.get(childChromosomes.size() - 1).getChromosomeId();
        chromosomeIdCounter++;

        return childChromosomes;
    }

    public void mutation() {
        Mutation mutation = new Mutation();
        mutation.mutation(population, this.classrooms, lowMutationRate, highMutationRate, isStable, this.invigilators);
    }

    public void replacement(int currentGeneration, int childChromosomesSize) {
        Replacement replacement = new Replacement();

        if (currentGeneration < 100) {
            replacement.randomReplacement(population, childChromosomesSize);
        } else {
            replacement.ageBasedReplacement(population, childChromosomesSize);
        }
    }

    public void updateAgesOfChromosomes() {
        for (Chromosome chromosome: population) {
            chromosome.setAge(chromosome.getAge() + 1);
        }
    }

    public double[] algorithm(boolean experiment, int experimentId) {
        int wantedExamScheduleCount = 3;
        int currentGeneration = 0;
        int generationsWithUnderImprovementThreshold = 0;
        int generationsWithoutImprovement = 0;
        int maxGenerations = Integer.parseInt(ConfigHelper.getProperty("MAX_GENERATIONS"));
        int toleratedGenerationsWithoutImprovement = Integer.parseInt(ConfigHelper.getProperty("GENERATIONS_WITHOUT_IMPROVEMENT"));

        ArrayList<Chromosome> populationTemp;
        ArrayList<Chromosome> childChromosomes;

        generateData();
        populationTemp = initializationAndEncode();
        calculateFitness(false, experiment, experimentId, currentGeneration);
        double initalBestFitness = 0;
        while (currentGeneration < maxGenerations && generationsWithoutImprovement < toleratedGenerationsWithoutImprovement) {//değiştirilebilir
            if (currentGeneration == 0) {
                initalBestFitness = findBestFitnessScore();
            }
            currentGeneration += 1;
            updateAgesOfChromosomes();
            //visualization(wantedExamScheduleCount, currentGeneration);
            bestFitnessScore = findBestFitnessScore();

            selectParents(currentGeneration);
            childChromosomes = crossover();
            mutation();
            replacement(currentGeneration, childChromosomes.size());
            populationTemp.addAll(childChromosomes);


            calculateFitness(true, experiment, experimentId, currentGeneration);
            logger.debug("population size: " + populationTemp.size());
            double lastBestFitnessScore = findBestFitnessScore();

            logger.info("Generation: " + currentGeneration);
            logger.info("bestFitnessScore: " + bestFitnessScore);
            logger.info("lastBestFitnessScore: " + lastBestFitnessScore);


            if (lastBestFitnessScore <= bestFitnessScore) {
                generationsWithoutImprovement += 1;
            } else {
                generationsWithoutImprovement = 0;
                isStable = false;
            }

            double improvement = lastBestFitnessScore - bestFitnessScore;
            logger.info("improvement: " + improvement);
            if (improvement < 0.0001) {
                generationsWithUnderImprovementThreshold++;
                logger.info("generationsWithUnderImprovementThreshold: " + generationsWithUnderImprovementThreshold);
            } else {
                generationsWithUnderImprovementThreshold = 0;
                isStable = false;
            }

            if (generationsWithUnderImprovementThreshold == 100) {
                logger.info("parameters are changing..");
                lowMutationRate += 0.001;
                highMutationRate += 0.01;
                crossoverRate += 0.02;

            } else if (generationsWithUnderImprovementThreshold == 250) {
                logger.info("stable");
                isStable = true;
                generationsWithUnderImprovementThreshold = 0;
            }

        }
        Fitness fitness = new Fitness(courses, students, classrooms, invigilators, startDate, endDate, startTime, endTime);
        Chromosome bestChromosome = findBestChromosome();
        HTMLHelper.visualizeBestChromosomeConstraintChecklist(fitness, bestChromosome);
        double convergenceRate = (findBestFitnessScore() - initalBestFitness) / currentGeneration;

        if (!experiment) {
            VisualizationHelper.generateFitnessPlots();
        }

        return new double[]{convergenceRate, findBestFitnessScore()};
    }

    private Chromosome findBestChromosome() {
        population.sort(Chromosome.sortChromosomesByFitnessScoreDescendingOrder);
        return population.get(0);
    }
}
