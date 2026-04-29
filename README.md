# CodeVal Setup Guide

Follow these steps to clone and run the project.

## Step 1: Open CMD and set database credentials

Open Command Prompt and run:

```cmd

set DB_USERNAME=your_mysql_username

set DB_PASSWORD=your_mysql_password

```

Optional (to save permanently for future CMD sessions):

```cmd

setx DB_USERNAME "your_mysql_username"

setx DB_PASSWORD "your_mysql_password"

```

## Step 2: Create MySQL database

Login to MySQL and create the database:

```sql

CREATE DATABASE codeval;

```

## Step 3: Clone the project and run

Clone the repository:

```cmd

git clone <repository-url>

cd codeval\codeval-service

```

Run the Spring Boot service:

```cmd

mvnw.cmd spring-boot:run

```

After this, you are all set.

 