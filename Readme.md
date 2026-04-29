//Microservice 1 - Authentication Service//

cd auth-service
$env:SPRING_PROFILES_ACTIVE="local"
mvn spring-boot:run

//Microservice 2 - Provider Service//

cd provider-service
$env:SPRING_PROFILES_ACTIVE="local"
mvn spring-boot:run

//Microservice 3 - Schedule Service//

cd schedule-service
$env:SPRING_PROFILES_ACTIVE="local"
mvn spring-boot:run

//Microservice 4 - Appointment Service//

cd appointment-service
$env:SPRING_PROFILES_ACTIVE="local"
mvn spring-boot:run

//Microservice 5 - Payment Service //

cd payment-service
$env:SPRING_PROFILES_ACTIVE="local"
mvn spring-boot:run

//Microservice 6 - Review Service //

cd review-service
$env:SPRING_PROFILES_ACTIVE="local"
mvn spring-boot:run

//Microservice 7 - Notification Service //

cd notification-service
$env:SPRING_PROFILES_ACTIVE="local"
mvn spring-boot:run

//Microservice 8 - Record Service //

cd record-service
$env:SPRING_PROFILES_ACTIVE="local"
mvn spring-boot:run

//Microservice 9 - Eureka Service //

cd eureka-service
mvn spring-boot:run