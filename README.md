<h1 align="center">TradeStream — Real-Time Financial Data Processor</h1>

<p align="center">
  <a href="https://github.com/VictoriousWealth/tradestream">
    <img src="https://img.shields.io/github/repo-size/VictoriousWealth/tradestream" alt="Repo Size">
  </a>
  <a href="https://github.com/VictoriousWealth/tradestream/issues">
    <img src="https://img.shields.io/github/issues/VictoriousWealth/tradestream" alt="Open Issues">
  </a>
  <a href="https://github.com/VictoriousWealth/tradestream/pulls">
    <img src="https://img.shields.io/github/issues-pr/VictoriousWealth/tradestream" alt="Pull Requests">
  </a>
  <a href="https://github.com/VictoriousWealth/tradestream/blob/main/LICENSE">
    <img src="https://img.shields.io/badge/LICENSE-CC%20BY--NC%204.0-blue.svg" alt="License">
  </a>
  <a href="https://github.com/VictoriousWealth/tradestream/commits/main">
    <img src="https://img.shields.io/github/last-commit/VictoriousWealth/tradestream" alt="Last Commit">
  </a>
</p>

<p align="center">
  Scalable, secure microservice-based system simulating real-time financial transaction processing.<br>
  Inspired by architecture and engineering practices used in modern financial institutions like JPMorgan Chase.<br><br>
  <a href="#project-overview"><strong>Explore the Project Overview »</strong></a>
</p>

---

<a name="table-of-contents"></a>
## 📑 Table of Contents

- [Project Overview](#project-overview)
- [Technology Stack](#technology-stack)
- [System Architecture](#system-architecture)
- [Getting Started](#getting-started)
- [Usage](#usage)
- [Roadmap](#roadmap)
- [Security Considerations](#security-considerations)
- [Documentation](#documentation)
- [Learning Objectives](#learning-objectives)
- [License](#license)
- [Contact](#contact)
- [Acknowledgements](#acknowledgements)

---

<a name="project-overview"></a>
## 🚀 Project Overview [↑ Top](#table-of-contents)

TradeStream is a backend portfolio project that demonstrates:

- Secure, production-grade APIs using **Java Spring Boot**
- Event-driven architecture with **Kafka or RabbitMQ**
- Decoupled microservices with independent **PostgreSQL & Redis** data stores
- Containerized deployment with **Docker & Docker Compose**
- Secure API authentication using **JWT**
- Real-time transaction processing simulation
- Cloud deployment on **AWS Lightsail**

⚠️ **Note:** This project is for educational and portfolio purposes only. It does not process real financial transactions.

---

<a name="technology-stack"></a> 
## 🛠️ Technology Stack [↑ Top](#table-of-contents)

<p align="center">

  <a href="https://www.java.com/" target="_blank">
    <img src="https://github.com/devicons/devicon/blob/v2.16.0/icons/java/java-original-wordmark.svg" title="Java" alt="Java" width="80px" />
  </a>

  <a href="https://spring.io/projects/spring-boot" target="_blank">
    <img src="https://github.com/devicons/devicon/blob/v2.16.0/icons/spring/spring-original-wordmark.svg" title="Spring Boot" alt="Spring Boot" width="80px" />
  </a>

  <a href="https://www.docker.com/" target="_blank">
    <img src="https://github.com/devicons/devicon/blob/v2.16.0/icons/docker/docker-plain-wordmark.svg" title="Docker" alt="Docker" width="80px" />
  </a>

  <a href="https://kubernetes.io/" target="_blank">
    <img src="https://github.com/devicons/devicon/blob/v2.16.0/icons/kubernetes/kubernetes-plain-wordmark.svg" title="Kubernetes" alt="Kubernetes" width="80px" />
  </a>

  <a href="https://www.postgresql.org/" target="_blank">
    <img src="https://github.com/devicons/devicon/blob/v2.16.0/icons/postgresql/postgresql-plain-wordmark.svg" title="PostgreSQL" alt="PostgreSQL" width="80px" />
  </a>

  <a href="https://redis.io/" target="_blank">
    <img src="https://github.com/devicons/devicon/blob/v2.16.0/icons/redis/redis-original-wordmark.svg" title="Redis" alt="Redis" width="80px" />
  </a>

  <a href="https://www.rabbitmq.com/" target="_blank">
    <img src="https://github.com/devicons/devicon/blob/v2.16.0/icons/rabbitmq/rabbitmq-original-wordmark.svg" title="RabbitMQ" alt="RabbitMQ" width="80px" />
  </a>

  <a href="https://aws.amazon.com/" target="_blank">
    <img src="https://github.com/devicons/devicon/blob/v2.16.0/icons/amazonwebservices/amazonwebservices-plain-wordmark.svg" title="AWS" alt="AWS" width="80px" />
  </a>

  <a href="https://www.terraform.io/" target="_blank">
    <img src="https://github.com/VictoriousWealth/VictoriousWealth/blob/main/terraform-icon-purple" title="Terraform" alt="Terraform" width="80px" />
  </a>

</p>



| Category             | Technology                              |
|----------------------|-----------------------------------------|
| Backend Framework    | Java Spring Boot                        |
| Stream Processing    | Kafka or RabbitMQ (configurable)        |
| Database             | PostgreSQL                              |
| Cache Layer          | Redis                                   |
| Authentication       | JWS & JWE (JWT Signed & JWT Encrypted)  |
| Containerization     | Docker, Docker Compose                  |
| CI/CD (MVP)          | GitHub Actions                          |
| Deployment           | AWS Lightsail                           |

**Planned Future Enhancements:** 

- Kubernetes for container orchestration  
- Terraform for infrastructure as code  
- Prometheus & Grafana for observability  
- Advanced API security measures  

---

<a name="system-architecture"></a>
## 🏗️ System Architecture [↑ Top](#table-of-contents)

> _See [`./docs/high-level-architecture-diagram.png`](./docs/high-level-architecture-diagram.png) for a full high level system diagram._

> _See [`./api-gateway/docs/api-gateway-flow.drawio.png`](./api-gateway/docs/api-gateway-flow.drawio.png) for a more detailed version focused on API Gateway._

> _See [`./authentication-service/docs/authentication-flow.drawio.png`](./authentication-service/docs/authentication-flow.drawio.png) for a more detailed version focused on the Authentication Service._

**Core Components:** 

- **API Gateway:** Public entry point, request routing, token validation  
- **Authentication Service:** Issues JWT tokens after successful login  
- **Transaction Processor:** Handles transaction logic, publishes events  
- **Market Data Consumer:** Placeholder for future real-time market data processing  
- **Message Broker:** Kafka or RabbitMQ for decoupled, event-driven communication  
- **PostgreSQL & Redis:** Each microservice manages its own independent data stores  

### 🔍 Service-Specific Docs

Each microservice has its own documentation with setup notes, endpoints, and future plans:

- [API Gateway](api-gateway/README.md)
- [User Registration Service](user-registration-service/README.md)
- [Authentication Service](authentication-service/README.md)
- [Transaction Processor](transaction-processor/README.md)
- [Market Data Consumer](market-data-consumer/README.md)

---

<a name="getting-started"></a>
## ⚙️ Getting Started [↑ Top](#table-of-contents)

### Prerequisites

- Docker & Docker Compose installed  
- Basic familiarity with Java, Spring Boot, and containers  

### Installation

```bash
git clone https://github.com/VictoriousWealth/tradestream.git
cd tradestream

# Build and run containers
docker-compose up --build
```

Access:

* API Gateway at `http://localhost:8080`
* Login, registration, transaction, and health endpoints per the [API Design](docs/api-design.md)

---

<a name="usage"></a>

## 💡 Usage [↑ Top](#table-of-contents)

This project simulates real-time transaction processing using microservices and event-driven patterns. Intended for:

* Backend portfolio demonstrations
* Learning microservice architecture principles
* Experimenting with containerized deployments
* Practicing secure API development for financial-like systems

---

<a name="roadmap"></a>

## 🛣️ Roadmap [↑ Top](#table-of-contents)

✅ MVP Core Features:

* [x] User Registration Service
* [x] Authentication Service
* [x] Transaction Processor
* [x] API Gateway
* [x] Event publishing via Kafka or RabbitMQ
* [x] Secure APIs with JWT
* [x] Containerized deployment on Lightsail

🔜 Future Enhancements:

* [ ] Market Data Generator
* [ ] Kubernetes deployment
* [ ] Infrastructure as code with Terraform
* [ ] Observability with Prometheus & Grafana
* [ ] Advanced security hardening

---

<a name="security-considerations"></a>

## 🔒 Security Considerations [↑ Top](#table-of-contents)

* Secure coding practices applied from Day 1
* JWT-based API authentication
* Special header(s) set by the API gateway that the other services will now check to double confirm origin of the request
* Future security roadmap includes:

  * Rate limiting
  * Secure HTTP headers
  * Vulnerability scanning

---

<a name="documentation"></a>

## 📚 Documentation [↑ Top](#table-of-contents)

* [Project Requirements & Design (PRD)](docs/tradestream-prd.pdf)
* [System Architecture Diagram](docs/architecture-diagram.png)
* [API Design](docs/api-design.md)
* [Planned Enhancements](docs/future-enhancements.md)

> Note all these documentation misses the user registration service as it is something I decided to add last minute 

---

<a name="learning-objectives"></a>

## 🎯 Learning Objectives [↑ Top](#table-of-contents)

TradeStream enables practical experience with:

* Secure backend and microservice design
* Event-driven architectures for real-time data
* Docker-based containerization
* Cloud deployment fundamentals
* Enterprise-grade system patterns for financial technology

---

<a name="license"></a>

## 📝 License [↑ Top](#table-of-contents)

This project is licensed under the **Creative Commons Attribution-NonCommercial 4.0 International (CC BY-NC 4.0)** license.

You are free to:

* Share and adapt the project
* Use it for educational, personal, or research purposes

**Commercial use is not permitted.**

See the full license [here](https://creativecommons.org/licenses/by-nc/4.0/) for details.

---

<a name="contact"></a>

## 📬 Contact [↑ Top](#table-of-contents)

**Nick Efe Oni**
[LinkedIn](https://www.linkedin.com/in/nick-efe-oni)
[GitHub](https://github.com/VictoriousWealth)

---

<a name="acknowledgements"></a>

## 🙏 Acknowledgements [↑ Top](#table-of-contents)

* Architectural patterns inspired by real-world fintech systems such as JPMorgan and BNY
* Security best practices influenced by **OWASP** guidelines
* Deployment and infrastructure ideas from **AWS** resources

---
