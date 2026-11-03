let questions = [];
let studentName = sessionStorage.getItem("studentName") || "";
let timerInterval = null;
let examSubmitted = false;

document.addEventListener("DOMContentLoaded", () => {
    const loginForm = document.getElementById("loginForm");
    const examForm = document.getElementById("examForm");

    if (loginForm) {
        loginForm.addEventListener("submit", login);
    }

    if (examForm) {
        if (!studentName) {
            window.location.href = "index.html";
            return;
        }
        document.getElementById("studentDisplay").textContent = studentName;
        loadQuestions();
    }

    if (document.getElementById("score")) {
        loadResult();
    }
});

async function login(event) {
    event.preventDefault();

    const name = document.getElementById("studentName").value.trim();
    const password = document.getElementById("password").value;
    const error = document.getElementById("loginError");

    try {
        const response = await fetch("/api/login", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({name, password})
        });

        const data = await response.json();

        if (!data.success) {
            error.textContent = data.message;
            return;
        }

        sessionStorage.setItem("studentName", data.name);
        window.location.href = "exam.html";
    } catch (e) {
        error.textContent = "Unable to connect to the Java server.";
    }
}

async function loadQuestions() {
    try {
        const response = await fetch("/api/questions");
        questions = await response.json();

        const container = document.getElementById("questions");
        container.innerHTML = questions.map((q, index) => `
            <div class="question-card">
                <div class="question-title">${index + 1}. ${escapeHtml(q.question)}</div>
                ${q.options.map((option, optionIndex) => `
                    <label class="option">
                        <input type="radio" name="q${index}" value="${optionIndex}">
                        ${String.fromCharCode(65 + optionIndex)}. ${escapeHtml(option)}
                    </label>
                `).join("")}
            </div>
        `).join("");

        document.querySelectorAll('input[type="radio"]').forEach(input => {
            input.addEventListener("change", updateProgress);
        });

        updateProgress();
        startTimer(10 * 60);

        document.getElementById("examForm").addEventListener("submit", submitExam);
    } catch (e) {
        showToast("Could not load questions.");
    }
}

function updateProgress() {
    let answered = 0;
    for (let i = 0; i < questions.length; i++) {
        if (document.querySelector(`input[name="q${i}"]:checked`)) {
            answered++;
        }
    }
    document.getElementById("progress").textContent =
        `${answered} / ${questions.length} answered`;
}

function startTimer(seconds) {
    const timer = document.getElementById("timer");
    let remaining = seconds;

    const tick = () => {
        const minutes = Math.floor(remaining / 60);
        const secs = remaining % 60;
        timer.textContent =
            `${String(minutes).padStart(2, "0")}:${String(secs).padStart(2, "0")}`;

        if (remaining <= 0) {
            clearInterval(timerInterval);
            submitExam(null, true);
            return;
        }
        remaining--;
    };

    tick();
    timerInterval = setInterval(tick, 1000);
}

async function submitExam(event, automatic = false) {
    if (event) event.preventDefault();
    if (examSubmitted) return;

    if (!automatic) {
        const unanswered = questions.length -
            document.querySelectorAll('input[type="radio"]:checked').length;

        if (unanswered > 0) {
            const ok = confirm(
                `You have ${unanswered} unanswered question(s). Submit anyway?`
            );
            if (!ok) return;
        }
    }

    examSubmitted = true;
    clearInterval(timerInterval);

    const answers = questions.map((q, index) => {
        const selected = document.querySelector(`input[name="q${index}"]:checked`);
        return selected ? Number(selected.value) : -1;
    });

    try {
        const response = await fetch("/api/submit", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({student: studentName, answers})
        });

        const result = await response.json();
        sessionStorage.setItem("examResult", JSON.stringify(result));
        window.location.href = "result.html";
    } catch (e) {
        examSubmitted = false;
        showToast("Could not submit the exam. Please try again.");
    }
}

function loadResult() {
    const resultText = sessionStorage.getItem("examResult");

    if (!resultText) {
        window.location.href = "index.html";
        return;
    }

    const result = JSON.parse(resultText);

    document.getElementById("resultStudent").textContent = result.student;
    document.getElementById("score").textContent =
        `${result.score}/${result.total}`;
    document.getElementById("percentage").textContent =
        `${result.percentage}%`;
    document.getElementById("correct").textContent = result.correct;
    document.getElementById("wrong").textContent = result.wrong;
    document.getElementById("unanswered").textContent = result.unanswered;

    document.getElementById("resultMessage").textContent =
        result.percentage >= 40
            ? "Congratulations! You have passed the examination."
            : "Keep practicing. You can improve your score!";

    const review = document.getElementById("answerReview");
    review.innerHTML = "<h3>Answer Review</h3>" +
        result.review.map((item, i) => {
            let cls = item.selected === -1
                ? "review-unanswered"
                : item.correct
                    ? "review-correct"
                    : "review-wrong";

            let status = item.selected === -1
                ? "Not answered"
                : item.correct
                    ? "Correct"
                    : "Wrong";

            return `
                <div class="review-item ${cls}">
                    <b>Q${i + 1}:</b> ${escapeHtml(item.question)}
                    <br>
                    <small>${status} | Correct answer: ${escapeHtml(item.correctAnswer)}</small>
                </div>
            `;
        }).join("");
}

function escapeHtml(text) {
    return String(text)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

function showToast(message) {
    const toast = document.getElementById("toast");
    if (!toast) return;
    toast.textContent = message;
    toast.style.display = "block";
    setTimeout(() => toast.style.display = "none", 3000);
}
