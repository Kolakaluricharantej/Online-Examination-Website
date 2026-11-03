# Online Examination Website

A simple Online Examination Website built using **HTML, CSS, JavaScript and Java**.

## Features
- Student Login
- Multiple-choice questions
- Countdown timer
- Automatic submission when time ends
- Automatic result calculation
- Result display with correct/incorrect answers
- Java backend with no external libraries

## Requirements
- JDK 17 or newer
- A web browser

## How to Run

### Windows
Open Command Prompt/PowerShell inside the project folder:

```powershell
javac -d out src/OnlineExamServer.java
java -cp out OnlineExamServer
```

Then open:

`http://localhost:8080`

### Login
Use any non-empty student name and password.

Demo:
- Student Name: `Shane`
- Password: `1234`

The login is intentionally simple for an academic demonstration.

## Project Structure

```text
Online_Examination_Website/
├── src/
│   └── OnlineExamServer.java
├── web/
│   ├── index.html
│   ├── exam.html
│   ├── result.html
│   ├── style.css
│   └── script.js
└── README.md
```
