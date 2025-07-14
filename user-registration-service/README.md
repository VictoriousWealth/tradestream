# 🚀 User Registration Service

* A simple user registration microservice

---

## 📘 Overview


* It allows the registration of new users using a provided username and password
* I built it because I want to learn how to build microservices after reading so much about them - this will be used in tradestream 
* I used Java SpringBoot, Flyway, Docker, and Gradle
* Any unique twist or feature? 
  It has an internal caller interceptor that only forwards requests from my api gateway by checking the header of each request  


## 📸 Screenshots

```bash
![./docs/example-curl-request-to-microservice.png]
```

---

## 🧠 What I Learned

* How to build a simple microservice 
* How to integrate Flyway to Spring Boot projects
* How to integrate interceptors into Spring Boot projects
* How to integrate Docker-compose.yml environment variables into Spring Boot projects

---

## 📦 Getting Started

Instructions to run it locally:

```bash
# Clone the repository
git clone https://github.com/VictoriousWealth/tradestream.git

# Navigate to the project directory
cd ./tradestream/user-registration-service

# Run the application
./gradlew clean build -x test 
```

Access:

* User Registration Service at `http://localhost:8081`

---

## 📄 License

This project is licensed under the **Creative Commons Attribution-NonCommercial 4.0 International (CC BY-NC 4.0)** license.

You are free to:

* Share and adapt the project
* Use it for educational, personal, or research purposes

**Commercial use is not permitted.**

See the full license [here](https://creativecommons.org/licenses/by-nc/4.0/) for details.

---

## 📬 Contact [↑ Top](#table-of-contents)

**Nick Efe Oni**
[LinkedIn](https://www.linkedin.com/in/nick-efe-oni)
[GitHub](https://github.com/VictoriousWealth)

---
