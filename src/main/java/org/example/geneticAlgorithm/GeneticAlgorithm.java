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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Random;

import static org.example.utils.DataStructureHelper.sortByValueDescending;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Data
public class GeneticAlgorithm {

    // TODO(Deniz) : Chromosome and Population classes can be avoid to
    //  reduce complexity

    private ArrayList<Course> courses = new ArrayList<>();
    private ArrayList<Invigilator> invigilators = new ArrayList<>();
    private ArrayList<Classroom> classrooms = new ArrayList<>();
    private ArrayList<Student> students = new ArrayList<>();
    private ArrayList<Timeslot> timeslots = new ArrayList<>();
    private ArrayList<Exam> exams = new ArrayList<>();
    private ArrayList<EncodedExam> encodedExams = new ArrayList<>();
    private ArrayList<EncodedExam> chromosome;
    private HashMap<String, ArrayList<?>> chromosomeForVisualization = new HashMap<>();
    private ArrayList<ArrayList<EncodedExam>> population = new ArrayList<>();
    private ArrayList<HashMap<String, ArrayList<?>>> populationForVisualization = new ArrayList<>();
    private Schedule schedule;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private int interval;
    private HashMap<ArrayList<EncodedExam>, Double> hardConstraintFitnessScores = new HashMap<>();
    private HashMap<ArrayList<EncodedExam>, Double> softConstraintFitnessScores = new HashMap<>();
    private static final Logger logger = LogManager.getLogger(GeneticAlgorithm.class);
    private ArrayList<ArrayList<EncodedExam>> parents = new ArrayList<>();
    double bestFitnessScore;
    int populationSize = Integer.parseInt(ConfigHelper.getProperty("POPULATION_SIZE"));
    private HashMap<ArrayList<EncodedExam>, Integer> chromosomeAgesMap = new HashMap<>();


    public void generateData() {
        HashMap<String, HashMap<String, ArrayList<Object>>> randomData = RandomDataGenerator.combineAllData();
        this.courses = RandomDataGenerator.generateCourseInstances(randomData.get("courseData"));
        this.courses = new ArrayList<>(courses.subList(0, Math.min(70, courses.size())));

        this.invigilators = RandomDataGenerator.generateInvigilatorInstances(randomData.get("invigilatorData"));
        this.invigilators = new ArrayList<>(invigilators.subList(0, Math.min(40, invigilators.size())));

        this.classrooms = RandomDataGenerator.generateClassroomInstances(randomData.get("classroomData"));

        this.students = RandomDataGenerator.generateStudentInstances(randomData.get("studentData"));
        this.students = new ArrayList<>(students.subList(0, Math.min(100, students.size())));

        this.startDate = LocalDate.parse(ConfigHelper.getProperty("START_DATE"));
        this.endDate = LocalDate.parse(ConfigHelper.getProperty("END_DATE")); // this date is not included
        this.startTime = LocalTime.parse(ConfigHelper.getProperty("START_TIME"));
        this.endTime = LocalTime.parse(ConfigHelper.getProperty("END_TIME"));
        this.interval = Integer.parseInt(ConfigHelper.getProperty("TIME_SLOT_INTERVAL"));
        this.schedule = RandomDataGenerator.generateSchedule(startDate, endDate, startTime, endTime, interval);
        this.timeslots = schedule.calculateTimeSlots();

        logger.info("Number of Students: " + students.size());
        logger.info("Number of Classroom: " + classrooms.size());
        logger.info("Number of invigilators: " + invigilators.size());
        logger.info("Number of courses: " + courses.size());
        logger.info("Number of timeslots: " + schedule.calculateMaxTimeSlots());

        HashMap<String, ArrayList<?>> resultCoursesStudents = Initialization.heuristicMapCoursesWithStudents(this.courses, this.students);
        this.courses = DataStructureHelper.castArrayList(resultCoursesStudents.get("courses"), Course.class);
        this.students = DataStructureHelper.castArrayList(resultCoursesStudents.get("students"), Student.class);
        logger.info("heuristicMapCoursesWithStudents finished.");

        File holidaysFile = new File(FileHelper.holidayFilePath);
        if (!holidaysFile.exists()) {
            FileHelper.saveHolidaysToFile();
        }

    }

    public ArrayList<ArrayList<EncodedExam>> initializationAndEncode() {
        int populationSize = Integer.parseInt(ConfigHelper.getProperty("POPULATION_SIZE"));
        for (int i = 0; i < populationSize; i++) {

            //logger.info("Population " + i);

            HashMap<String, ArrayList<?>> resultExams = Initialization.createExamInstances(this.courses);
            this.exams = DataStructureHelper.castArrayList(resultExams.get("exams"), Exam.class);
            logger.info("createExamInstances finished.");
            Random rand = new Random();

            Collections.shuffle(this.exams, new Random(rand.nextInt(10000)));
            Collections.shuffle(this.invigilators, new Random(rand.nextInt(10000)));
            Collections.shuffle(this.classrooms, new Random(rand.nextInt(10000)));

            HashMap<String, ArrayList<?>> resultCoursesInvigilators = Initialization.heuristicMapExamsWithInvigilators(exams, invigilators);
            this.exams = DataStructureHelper.castArrayList(resultCoursesInvigilators.get("exams"), Exam.class);
            logger.info("heuristicMapExamsWithInvigilators finished.");

            Collections.shuffle(exams, new Random(rand.nextInt(10000)));
            HashMap<String, ArrayList<?>> resultCoursesClassrooms = Initialization.heuristicMapExamsWithClassrooms(exams, classrooms);
            this.exams = DataStructureHelper.castArrayList(resultCoursesClassrooms.get("exams"), Exam.class);
            logger.info("heuristicMapExamsWithClassrooms finished.");

            Collections.shuffle(exams, new Random(rand.nextInt(10000)));
            HashMap<String, ArrayList<?>> resultCoursesTimeslots = Initialization.heuristicMapExamsWithTimeslots(exams, timeslots);
            this.exams = DataStructureHelper.castArrayList(resultCoursesTimeslots.get("exams"), Exam.class);
            logger.info("heuristicMapExamsWithTimeslots finished.");

            encode();

            /*Optional<Course> filteredCourseOpt = courses.stream().filter(course -> course.getCourseCode().equals("KKW219")).findAny();
            Course filteredCourse = filteredCourseOpt.orElse(null);

            assert filteredCourse != null;
            logger.info("####" + filteredCourse.getRegisteredStudents());*/

            // for visualization purposes and reduce complexity
            this.chromosomeForVisualization.put("exams", new ArrayList<>(exams));
            this.chromosomeForVisualization.put("invigilators", new ArrayList<>(invigilators));
            this.chromosomeForVisualization.put("classrooms", new ArrayList<>(classrooms));
            this.populationForVisualization.add(new HashMap<>(chromosomeForVisualization));
            reset();
        }
        return population;
    }

    public void encode() {
        this.encodedExams = Encode.encode(this.exams);
        this.chromosome = encodedExams;
        this.population.add(chromosome);
        logger.info("Encode is finished.");
    }

    public void visualization(int wantedExamScheduleCount) {
        for (int k = 0; k < wantedExamScheduleCount; k++) {
            VisualizationHelper.generateReports(courses, students, classrooms, interval);

            // Exam Schedule :
            // this will visualize a random exam schedule from population

            // this exam schedule is for invigilators not for students
            Random rand = new Random();
            int n = rand.nextInt(populationForVisualization.size());
            HashMap<String, ArrayList<?>> randomInfo = populationForVisualization.get(n);
            ArrayList<EncodedExam> randomExamScheduleForInvigilators = Encode.encode(DataStructureHelper.castArrayList(randomInfo.get("exams"), Exam.class));
            HTMLHelper.generateExamTable(startTime, endTime, startDate, endDate, interval, randomExamScheduleForInvigilators, "Exam Schedule-" + n + " for Invigilators");

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
            HTMLHelper.generateExamTable(startTime, endTime, startDate, endDate, interval, randomExamScheduleForStudents, "Exam Schedule-" + n + " for Students");
            HTMLHelper.generateExamTableDila(startTime, endTime, startDate, endDate, interval, randomExamScheduleForStudents, "Exam ScheduleDila-" + n + " for Students");

            // Reports that are changing : invigilators, classrooms, exam schedules
            HTMLHelper.generateInvigilatorReport(DataStructureHelper.castArrayList(randomInfo.get("invigilators"), Invigilator.class), "graphs/invigilator_report_" + n + ".html", "Invigilator Report");
            HTMLHelper.generateClassroomReport(DataStructureHelper.castArrayList(randomInfo.get("classrooms"), Classroom.class), "graphs/classroom_report_" + n + ".html", "Classroom Report");
            HTMLHelper.generateExamReport(DataStructureHelper.castArrayList(randomInfo.get("exams"), Exam.class), "graphs/exams_" + n + ".html", "Exam Schedule");
        }

    }

    public void reset() {
        // reset classrooms and invigilators
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

    public void calculateFitness() {
        // make a hashmap with encoded exam as a key
        // and fitness score as a value
        Fitness fitness = new Fitness(courses, students, classrooms, invigilators, startDate, endDate, startTime, endTime);
        ArrayList<double[]> hardConstraintScoresList = new ArrayList<>();
        ArrayList<double[]> softConstraintScoresList = new ArrayList<>();
        for (ArrayList<EncodedExam> chromosome : population) {
            double[][] scores = fitness.fitnessScore(chromosome);
            double[] hardConstraintScores = scores[0];
            double[] softConstraintScores = scores[1];

            hardConstraintScoresList.add(hardConstraintScores);
            softConstraintScoresList.add(softConstraintScores);

            double hardFitnessScore = hardConstraintScores[hardConstraintScores.length - 1];
            hardConstraintFitnessScores.put(chromosome, hardFitnessScore);
            double softFitnessScore = softConstraintScores[softConstraintScores.length - 1];
            softConstraintFitnessScores.put(chromosome, softFitnessScore);

        }

        // sort hashmaps based on fitness scores, this tables only contain fitness scores
        hardConstraintFitnessScores = sortByValueDescending(hardConstraintFitnessScores);
        softConstraintFitnessScores = sortByValueDescending(softConstraintFitnessScores);

        // visualize
//        for (ArrayList<EncodedExam> chromosome : hardConstraintFitnessScores.keySet()) {
//            logger.info("Hashcode of Exam Schedule: " + chromosome.hashCode() + ", Score: " + hardConstraintFitnessScores.get(chromosome));
//        }

        // this tables contain all the fitness function scores
        FileHelper.writeHardFitnessScoresToFile(hardConstraintScoresList, "graphs/fitness_scores_HARD.csv");
        FileHelper.writeSoftFitnessScoresToFile(softConstraintScoresList, "graphs/fitness_scores_SOFT.csv");
    }
    public double findBestFitnessScore() {
        return Collections.max(hardConstraintFitnessScores.values());
    }

    public void selectParents() {
        calculateFitness();
        Selection selection = new Selection();
        parents = selection.rouletteWheelSelection(this.hardConstraintFitnessScores);
    }

    public ArrayList<ArrayList<EncodedExam>> crossover() {
        Crossover crossover = new Crossover();
        return crossover.onePointCrossover(parents);
    }

    public void mutation() {
        Mutation mutation = new Mutation();
        mutation.mutation(this.hardConstraintFitnessScores, population);
    }

    public void replacement(int currentGeneration, int childChromosomesSize) {
        Replacement replacement = new Replacement();
        logger.info(population);
        if (currentGeneration == 1) {
            replacement.fitnessBasedReplacement(this.hardConstraintFitnessScores, childChromosomesSize, population);
        } else {
            replacement.ageBasedReplacement(chromosomeAgesMap, childChromosomesSize, population);
        }
        logger.info(population);
        logger.info("");

    }

    public void setAgeToChromosomes(ArrayList<ArrayList<EncodedExam>> chromosomeList) {
        for (ArrayList<EncodedExam> chromosome: chromosomeList) {
            chromosomeAgesMap.put(chromosome, chromosomeAgesMap.getOrDefault(chromosome, 0) + 1);
        }
    }
}
