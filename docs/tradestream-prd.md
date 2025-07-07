
# **Table of Contents**

* [1. Title & Document Control](#1-title--document-control)
* [2. Executive Summary](#2-executive-summary)
* [2A. Technology Overview](#2a-technology-overview)
* [3. Goals & Objectives](#3-goals--objectives)
* [4. Scope & Deliverables](#4-scope--deliverables)
* [5. High-Level Architecture (Updated with Authentication Service)](#5-high-level-architecture-updated-with-authentication-service)
* [6. Technical Design](#6-technical-design)
* [7. Assumptions & Constraints](#7-assumptions--constraints)
* [8. Risks & Mitigations](#8-risks--mitigations)
* [9. Timeline & Milestones](#9-timeline--milestones)
* [10. References & Resources](#10-references--resources)
* [11. Appendix](#11-appendix)

---

# **Section 1: Title & Document Control** [↑ Top](#table-of-contents)

### **1\. Title & Document Control** [↑ Section Top](#1-title--document-control)

| Field | Content |
| ----- | ----- |
| **Project Name** | TradeStream — Real-Time Financial Data Processor |
| **Version** | 1.0 (Finalised) — Follows semantic versioning: 0.x for drafts, 1.0 for first release, 1.x for iterative improvements.  |
| **Author** | Nick Efe Oni |
| **Date** | Fri, 04 July 2025 |
| **Reviewers** | N/A |

---

## **2\. Executive Summary** [↑ Top](#table-of-contents)

**TradeStream — Real-Time Financial Data Processor** is a scalable, resilient microservice-based system designed to simulate the processing of real-time financial transactions and market data streams. The project reflects the architectural principles and engineering practices commonly used in modern financial institutions, such as JPMorgan Chase, where secure, high-throughput, fault-tolerant systems are essential for reliable operations.

The system is being developed using industry-standard technologies, including Java Spring Boot for backend services, Kafka or RabbitMQ for real-time stream processing, PostgreSQL and Redis for data storage and performance optimization, and Docker for containerized deployment. Automated build and testing processes are integrated through GitHub Actions, while AWS Lightsail is used as the initial cloud deployment platform.

TradeStream is designed with future scalability and production-readiness in mind. Planned enhancements include Kubernetes for container orchestration, Terraform for infrastructure as code, and observability tooling such as Prometheus and Grafana. The system incorporates secure coding practices from the outset, with iterative improvements planned to strengthen API security, resilience, and alignment with industry-standard security principles.

As a portfolio and learning project aligned with JPMorgan Chase’s technology standards, TradeStream demonstrates technical competence, secure engineering practices, and a clear understanding of the challenges involved in building modern, scalable financial software platforms.

---

## **2A. Technology Overview** [↑ Section Top](#2-executive-summary)

This section outlines the intended technology stack for TradeStream. It distinguishes between technologies confirmed for the Minimum Viable Product (MVP) and those planned as future enhancements to align with enterprise-grade scalability, resilience, and security standards.

---

### **Minimum Viable Product (MVP) Technology Stack** [↑ Section Top](#2-executive-summary)

For the MVP phase, all microservices will be containerized and deployed on a single AWS Lightsail instance for simplified management and cost efficiency. Future iterations will explore multi-instance deployment and/or Kubernetes-based orchestration to reflect production-grade microservice infrastructure patterns.

| Category | Technology | Purpose |
| ----- | ----- | ----- |
| Backend Framework | Java Spring Boot | Build secure, scalable, resilient APIs |
| Stream Processing | Kafka or RabbitMQ | Real-time data ingestion and event streaming |
| Database | PostgreSQL | Persistent storage for transactional data |
| Cache Layer | Redis | In-memory caching for performance optimization |
| Containerization | Docker | Isolate and package microservices |
| Authentication | JWT (JSON Web Tokens) | Secure API access and user authentication |
| Development Workflow | Git \+ GitHub \+ GitHub Actions (CI) | Version control and automated testing |
| Deployment | AWS Lightsail | Cloud hosting environment for deployed services |

---

### **Planned / Future Enhancements** [↑ Section Top](#2-executive-summary)

The following enhancements are planned for future development phases to align TradeStream more closely with enterprise-grade production standards:

| Category | Technology | Purpose |
| ----- | ----- | ----- |
| Container Orchestration | Kubernetes (K8s) | Automated scaling, service discovery, fault tolerance for microservices |
| Infrastructure as Code | Terraform | Provision and manage cloud infrastructure (e.g., AWS Lightsail or EC2) through code |
| Observability | Prometheus, Grafana (Optional) | System metrics collection, monitoring, and operational dashboards |
| Security Enhancements | Hardened API security, rate-limiting, advanced input validation, secure coding best practices | Improve resilience against common attack vectors and align with HTB-style security principles |
| CI/CD Extension | Enhanced CI/CD pipeline with automated Docker image publishing and optional deployment to AWS or Kubernetes | Enable seamless, reliable software delivery and updates |

---

### **Assumptions & Notes** [↑ Section Top](#2-executive-summary)

* **Stream Processing Choice:** Final decision between Kafka and RabbitMQ will be made during technical design based on project needs and ease of setup.  
* **Future Enhancements:** Kubernetes, Terraform, and Observability tooling will be explored in later stages to demonstrate system scalability, reproducibility, and production-grade practices.  
* **Security Focus:** The project will follow secure coding principles from the outset, with iterative hardening planned to reflect real-world financial system security expectations.

---

## **3\. Goals & Objectives** [↑ Top](#table-of-contents)

### **3.1 Project Purpose**  [↑ Section Top](#3-goals--objectives)

The purpose of TradeStream is to design, build, and deploy a scalable, resilient, and secure microservice-based system that simulates the processing of real-time financial transactions and market data streams. This project serves as a technical portfolio piece and a practical learning exercise, demonstrating backend engineering skills aligned with the standards and technologies used in large-scale financial services environments, such as those at JPMorgan Chase.

---

### **3.2 Technical Objectives**  [↑ Section Top](#3-goals--objectives)

The key technical objectives of TradeStream are:

* **Design and implement secure, production-grade APIs** using Java Spring Boot.  
* **Simulate real-time data processing** through event-driven architecture leveraging Kafka or RabbitMQ.  
* **Implement a robust data storage layer** combining PostgreSQL for transactional data and Redis for performance optimization.  
* **Containerize system components using Docker** to enable isolated, scalable deployment.  
* **Establish a CI pipeline with GitHub Actions** for automated building, testing, and deployment workflows.  
* **Deploy the system to AWS Lightsail**, demonstrating practical understanding of cloud infrastructure.  
* **Adopt secure coding practices** including API authentication, input validation, and resilience against common attack vectors.

---

### **3.3 Learning Objectives**  [↑ Section Top](#3-goals--objectives)

In addition to technical deliverables, TradeStream aims to achieve the following personal and professional development objectives:

* Deepen expertise in backend system design and microservice architecture.  
* Gain practical experience with event-driven data processing patterns.  
* Strengthen understanding of secure software development practices.  
* Enhance familiarity with containerization and cloud deployment workflows.  
* Demonstrate the ability to plan, structure, and execute a project following professional engineering standards.  
* Build a portfolio project that reflects readiness for software engineering roles in financial technology environments.

---

### **3.4 Future Enhancement Objectives**  [↑ Section Top](#3-goals--objectives)

To align with enterprise-grade system expectations, the following future enhancements are planned:

* Incorporation of Kubernetes for container orchestration, enabling automated scaling and resilience.  
* Introduction of Terraform to provision and manage infrastructure as code.  
* Integration of observability tooling such as Prometheus and Grafana for system monitoring and operational insight.  
* Expansion of the CI/CD pipeline to support automated Docker image publishing and streamlined cloud or Kubernetes deployment.  
* Iterative security hardening to further align the system with real-world financial security standards.

---

## **4\. Scope & Deliverables** [↑ Top](#table-of-contents)

### **4.1 Project Scope**  [↑ Section Top](#4-scope--deliverables)

TradeStream will deliver a scalable, resilient, and secure microservice-based system that simulates the processing of real-time financial transactions and market data streams. The project is intended as a portfolio and learning exercise to showcase backend development, secure engineering, and system design practices aligned with standards used in financial services environments such as JPMorgan Chase.

**Initial MVP Scope Includes:**

* Design and development of core microservices using Java Spring Boot  
* Real-time data processing via Kafka or RabbitMQ  
* PostgreSQL for persistent data storage  
* Redis for caching layer to enhance performance  
* Secure REST APIs with JWT-based authentication and input validation  
* Containerization of all components using Docker  
* CI pipeline for automated build and testing with GitHub Actions  
* Cloud deployment to a single AWS Lightsail instance, hosting all containerized services for simplified management and demonstration

**Out of Scope for MVP:**

* Full production-grade distributed infrastructure (e.g., multiple Lightsail instances or Kubernetes cluster)  
* Infrastructure as Code (e.g., Terraform)  
* Advanced observability tooling (e.g., Prometheus, Grafana)  
* High availability and automated scaling mechanisms  
* Extended security hardening, beyond basic authentication and input validation

---

### **4.2 Planned Future Deliverables** [↑ Section Top](#4-scope--deliverables)

In later phases of development, the project may incorporate:

* Multi-instance or Kubernetes-based deployment to reflect real-world microservice orchestration patterns  
* Infrastructure provisioning with Terraform for consistent, reproducible environments  
* Integration of observability tools such as Prometheus and Grafana for system monitoring  
* **Extended security hardening**, including:  
  * Rate limiting and request throttling to mitigate abuse and basic DDoS attacks  
  * Role-Based Access Control (RBAC) for fine-grained authorization  
  * Secure HTTP headers and hardened system configurations  
  * Robust error handling to prevent information leakage  
  * Vulnerability scanning using tools such as OWASP ZAP or dependency checkers  
  * Token hardening strategies, including refresh tokens and reduced token lifespan  
* CI/CD pipeline extension for automated Docker image publishing and streamlined cloud or Kubernetes deployment

---

### **4.3 Deliverables Summary** [↑ Section Top](#4-scope--deliverables)

| Deliverable | Included in MVP | Planned for Future Phases |
| ----- | ----- | ----- |
| Spring Boot Microservices | ✔️ | — |
| Event-Driven Architecture (Kafka/RabbitMQ) | ✔️ | — |
| PostgreSQL and Redis Data Layer | ✔️ | — |
| Secure APIs with JWT and Input Validation | ✔️ | — |
| Docker Containerization | ✔️ | — |
| CI Pipeline with GitHub Actions | ✔️ | — |
| Single Lightsail Instance Deployment | ✔️ | — |
| Multi-Instance or K8s Deployment | — | ✔️ |
| Infrastructure as Code (Terraform) | — | ✔️ |
| Observability Tooling | — | ✔️ |
| **Extended Security Hardening** | — | ✔️ |
| Full CI/CD Pipeline with Automated Deployments | — | ✔️ |

---

# **5\. High-Level Architecture (Updated with Authentication Service)** [↑ Top](#table-of-contents)

### **5.1 Architectural Overview** [↑ Section Top](#5-high-level-architecture-updated-with-authentication-service)

TradeStream is designed as a scalable, resilient, and secure microservice-based system following modern architectural principles used in financial services environments. The system is composed of loosely coupled microservices, an API Gateway, a message broker for event-driven communication, a dedicated Authentication Service for secure token issuance, and a layered data storage approach incorporating both persistent and in-memory data stores.

The architecture separates concerns clearly between **external-facing components** and **internal system logic**, ensuring secure, maintainable, and future-scalable system design.

---

### **5.2 High-Level System Diagram** [↑ Section Top](#5-high-level-architecture-updated-with-authentication-service)

<img src="https://github.com/VictoriousWealth/tradestream/blob/main/docs/high-level-architecture-diagram.png" alt="High-Level System Diagram">

---

### **5.3 Component Descriptions** [↑ Section Top](#5-high-level-architecture-updated-with-authentication-service)

| Component | Description |
| :---- | ----- |
| **Client** | External application or tool (e.g., Postman, browser) sending authenticated requests via HTTPS. |
| **API Gateway** | The system's only public entry point. Handles authentication enforcement (JWT validation), request routing, and security checks. Does not issue tokens. |
| **Authentication Service** | Dedicated microservice responsible for validating user credentials and issuing JWT tokens upon successful login. |
| **Transaction Processor** | Microservice responsible for handling transaction-related logic, database persistence, and event publishing. |
| **Market Data Consumer** | Microservice consuming simulated market data from the message broker, performing data processing and storage. |
| **Future Services** | Additional services (e.g., Fraud Detection, Notifications) consuming events or exposing APIs. |
| **Message Broker (Kafka/RabbitMQ)** | Facilitates decoupled, real-time, event-driven communication between services. |
| **PostgreSQL** | Relational database for persistent storage of service-specific data. Each microservice owns its own database. |
| **Redis Cache** | In-memory caching layer for performance optimization, reducing database load for frequently accessed data. |

---

### **5.4 System Boundaries** [↑ Section Top](#5-high-level-architecture-updated-with-authentication-service)

| Boundary | Description |
| :---- | ----- |
| **External** | Includes the Client and API Gateway, the only publicly exposed component. Client sends login requests and subsequent authenticated requests through the API Gateway. |
| **Internal** | Includes all microservices, the Authentication Service, message broker, databases, and Redis caches. These are isolated within a private, containerized network, inaccessible directly from the public internet. |

---

### **5.5 Key Architectural Principles** [↑ Section Top](#5-high-level-architecture-updated-with-authentication-service)

* **Microservice Isolation:** Each service owns its logic and data storage (PostgreSQL \+ Redis).  
* **Authentication Separation:** A dedicated Authentication Service handles credential validation and token issuance.  
* **API Gateway Control:** Centralized entry point enforcing security and request routing, but does not issue tokens.  
* **Event-Driven Architecture:** Asynchronous, decoupled communication using Kafka/RabbitMQ.  
* **Containerization:** All services are containerized using Docker, simplifying deployment and isolation.  
* **Scalability:** Architecture designed for future scaling using Kubernetes and Infrastructure as Code (Terraform).

---

# **6\. Technical Design** [↑ Top](#table-of-contents)

This section breaks down the internal structure of TradeStream's system components, key APIs, data flows, and security considerations. It provides a practical view of how the system will be implemented, building on the High-Level Architecture.

---

## **6.1 Microservice Breakdown** [↑ Section Top](#6-technical-design)

| Microservice | Responsibilities |
| ----- | ----- |
| **Authentication Service** | Validates user credentials, issues JWT tokens upon successful login. Handles no business logic beyond authentication. |
| **API Gateway** | Public entry point for all client requests. Validates JWT tokens, enforces security, and routes requests to internal microservices. |
| **Transaction Processor** | Processes transaction-related logic. Handles persistent storage, caching, and publishes transaction events to the message broker. |
| **Market Data Consumer** | Consumes simulated real-time market data from the message broker and processes it as required. |
| **Future Services** | Designed to follow the same architectural patterns (e.g., Fraud Detection, Notification Service). |

---

## **6.2 Key APIs** [↑ Section Top](#6-technical-design)

### **Authentication Service** [↑ Top](#table-of-contents)

| Method | Endpoint | Description |
| :---- | ----- | ----- |
| POST | `/auth/login` | Accepts username/password, returns JWT if credentials are valid. |

---

### **API Gateway** [↑ Section Top](#6-technical-design)

| Method | Endpoint | Description |
| :---- | ----- | ----- |
| Any | `/*` | Handles routing for all requests to internal services after validating JWT. |
| POST | `/auth/login` | Forwards login requests to Authentication Service. |

---

### **Transaction Processor** [↑ Section Top](#6-technical-design)

| Method | Endpoint | Description |
| :---- | ----- | ----- |
| POST | `/transactions` | Creates a new transaction. Requires valid JWT. |
| GET | `/transactions/{id}` | Retrieves transaction details. Requires valid JWT. |
| GET | `/health` | Basic health check endpoint. |

---

### **Market Data Consumer** [↑ Section Top](#6-technical-design)

| Method | Endpoint | Description |
| :---- | ----- | ----- |
| GET | `/health` | Basic health check endpoint. |

---

## **6.3 Data Flow Example: Transaction Creation** [↑ Section Top](#6-technical-design)

1. Client sends `POST /transactions` to the API Gateway with a valid JWT.  
2. API Gateway validates the token and routes the request to the Transaction Processor.  
3. Transaction Processor:  
   * Validates request body.  
   * Stores transaction in **PostgreSQL**.  
   * Publishes a transaction event to **Kafka/RabbitMQ**.  
   * Optionally caches transaction details in **Redis**.  
4. Other services (e.g., Market Data Consumer, future Fraud Detection) consume events if needed.  
5. Transaction Processor returns response to API Gateway, which forwards it to the client.

---

## **6.4 Data Storage Overview** [↑ Section Top](#6-technical-design)

| Component | Storage Used |
| :---- | ----- |
| **Transaction Processor** | PostgreSQL for persistent storage, Redis for performance optimization. |
| **Market Data Consumer** | PostgreSQL for market data, Redis for caching (if applicable). |
| **Authentication Service** | (Optional) May use its own PostgreSQL for user records or mock users for MVP. |

---

## **6.5 Message Broker Usage** [↑ Section Top](#6-technical-design)

✔ The system uses **Kafka or RabbitMQ** for real-time, decoupled event-driven communication.  
✔ Initial topics include:

* `transaction-events`: published by Transaction Processor, consumed by other services.  
  ✔ Future topics may include:  
* `fraud-alerts`, `notifications`, etc.

---

## **6.6 Security Considerations** [↑ Section Top](#6-technical-design)

* **JWT Authentication:**  
  * Tokens issued by Authentication Service.  
  * Validated by API Gateway on all requests.  
* **Input Validation:**  
  * Applied within each microservice to prevent injection attacks.  
* **Future Security Enhancements:**  
  * Rate limiting, RBAC, secure headers, vulnerability scanning planned as the system matures.

---

# **7\. Assumptions & Constraints** [↑ Top](#table-of-contents)

This section outlines the technical assumptions and project constraints that inform the design, implementation, and delivery of the TradeStream system. These factors should be considered throughout development to ensure realistic expectations and technical feasibility.

---

## **7.1 Technical Assumptions** [↑ Section Top](#7-assumptions--constraints)

1. **Docker Availability:**  
   All system components will be containerized using Docker. It is assumed that Docker is available and operational on all development and deployment environments.  
2. **Single-Instance Deployment (MVP):**  
   The MVP will be deployed to a single AWS Lightsail instance. The system is designed with future multi-instance or Kubernetes-based deployment in mind, but this is not required initially.  
3. **Internal Network Isolation:**  
   All microservices, databases, caches, and the message broker will reside within a private, containerized network. Only the API Gateway will be exposed publicly.  
4. **Message Broker Choice:**  
   The final selection between **Kafka** and **RabbitMQ** will be made during technical implementation based on ease of setup, learning objectives, and project requirements. The system architecture is compatible with either.  
5. **Independent Data Stores per Microservice:**  
   Each microservice is responsible for its own PostgreSQL and Redis instances, reflecting a true microservice architecture.  
6. **JWT Authentication Model:**  
   * A dedicated Authentication Service will issue JWT tokens upon successful login.  
   * The API Gateway will validate tokens on all incoming requests.  
   * Internal microservices trust requests forwarded by the API Gateway without re-validating tokens.  
7. **Simulated Data Scope:**  
   The system simulates real-time financial transactions and market data streams for educational and portfolio purposes only. No real financial data or transactions are processed.  
8. **Security Focus (MVP):**  
   Secure coding practices (e.g., input validation, API authentication) will be applied from the outset. However, advanced security hardening measures (e.g., rate limiting, RBAC, vulnerability scanning) are planned for future phases.

---

## **7.2 Project Constraints** [↑ Section Top](#7-assumptions--constraints)

1. **Learning Project Scope:**  
   TradeStream is a self-initiated portfolio and learning project. It is not intended for production use or commercial deployment.  
2. **Time and Resource Constraints:**  
   Development is subject to individual availability and learning pace. No full-time development team or dedicated resources are assumed.  
3. **Technology Familiarity:**  
   * The project leverages Java Spring Boot, Docker, and basic AWS infrastructure, aligning with the author's existing skill set.  
   * Kubernetes, Terraform, and advanced observability tooling are considered future learning objectives and will not be implemented in the MVP.  
4. **Cloud Resource Limitations:**  
   Deployment is limited to AWS Lightsail for the MVP to ensure cost control and manageability. No assumptions are made regarding access to higher-tier cloud resources during initial development.  
5. **Simplified User Management (MVP):**  
   The Authentication Service may use a hardcoded or minimal user database for demonstration purposes. Full production-grade user management is not within the MVP scope.  
6. **Exclusion of Market Data Generator (For Now):**  
   While the system references simulated market data streams, no dedicated Market Data Generator component is included in the current PRD. This may be introduced in future revisions.

---

# **8\. Risks & Mitigations** [↑ Top](#table-of-contents)

This section identifies potential technical and project-related risks that may impact the successful delivery of TradeStream. Where possible, proactive mitigation strategies have been defined to reduce their likelihood or potential impact.

---

## **8.1 Technical Risks** [↑ Section Top](#8-risks--mitigations)

| Risk | Mitigation Strategy |
| ----- | ----- |
| Lack of Kubernetes expertise may delay future scaling plans | Kubernetes is excluded from MVP. Kubernetes will be explored gradually as a controlled, future enhancement. |
| Unfamiliarity with Kafka/RabbitMQ may complicate setup | Start with simplified configurations. If Kafka proves complex, RabbitMQ will be used for initial setup. |
| Security vulnerabilities due to limited production hardening | Apply basic secure coding practices from Day 1\. Iterative security hardening planned for future phases. |
| Complexity of multi-service architecture increases integration challenges | Start with a minimal set of services (Authentication Service, API Gateway, Transaction Processor) to manage complexity. |

---

## **8.2 Project Risks** [↑ Section Top](#8-risks--mitigations)

| Risk | Mitigation Strategy |
| ----- | ----- |
| Limited personal time and resources may delay project delivery | Development milestones are flexible to accommodate learning pace and availability. |
| Cloud infrastructure costs exceeding budget | MVP deployed to cost-effective AWS Lightsail. Higher-cost resources avoided until future phases. |
| Overcomplication of system design during learning phase | Focus on building a functional, secure MVP before introducing advanced tools like Kubernetes or Terraform. |
| Difficulty demonstrating real-world realism without full market data simulation | Market Data Generator is excluded from MVP to avoid overcomplication. It may be introduced in a future, focused iteration. |

---

## **8.3 General Risk Considerations** [↑ Section Top](#8-risks--mitigations)

* The project is intentionally scoped as a **portfolio and learning exercise**, not a commercial product, reducing risk exposure.  
* Risks related to incomplete functionality, limited scalability, or simplified security are acceptable within the learning-focused project constraints.

---

✅ **Risks & Mitigations locked in.**

Here’s a clean, realistic draft for **Section 9: Timeline & Milestones** for your TradeStream PRD, tailored to a self-driven, learning-focused project.

---

# **9\. Timeline & Milestones** [↑ Top](#table-of-contents)

This section outlines an estimated high-level timeline for delivering TradeStream, including key development phases and milestones. The timeline reflects realistic learning objectives, resource availability, and project scope.

---

## **9.1 High-Level Timeline (Estimated)** [↑ Section Top](#9-timeline--milestones)

| Phase | Estimated Target | Description |
| :---- | ----- | ----- |
| **Project Planning & PRD Finalization** | July 2025 (Completed) | Finalize project scope, architecture, and PRD document. |
| **MVP Development \- Phase 1** | July to August 2025 | \- Develop Authentication Service, API Gateway, Transaction Processor. \- Implement basic JWT authentication and secure API endpoints. |
| **MVP Development \- Phase 2** | August 2025 | \- Integrate PostgreSQL and Redis for Transaction Processor. \- Implement event publishing to Kafka or RabbitMQ. |
| **MVP Containerization & Deployment** | Late August 2025 | \- Containerize all services with Docker. \- Deploy to AWS Lightsail for demonstration. |
| **Basic Testing & System Validation** | August to September 2025 | \- Functional testing of core APIs and system interactions. \- Security checks (input validation, JWT verification). |
| **Future Enhancements Exploration** | Post-September 2025 (Flexible) | \- Gradually introduce Terraform, Kubernetes, observability tools, and advanced security measures. |

---

## **9.2 Key Milestones** [↑ Section Top](#9-timeline--milestones)

✔ **PRD Finalized** — July 2025  
✔ **Authentication Service Functional** — August 2025  
✔ **Transaction Processing Functional with DB & Redis** — August 2025  
✔ **Event-Driven Messaging Functional** — August 2025  
✔ **Containerized MVP Deployed to Lightsail** — August 2025  
✔ **MVP Demonstration Ready** — September 2025  
✔ **Future Enhancements Exploration Begins** — Post-MVP

---

## **9.3 Notes** [↑ Section Top](#9-timeline--milestones)

* The timeline is intentionally flexible to accommodate learning pace and evolving technical understanding.  
* Project phases may overlap or adjust based on real-world challenges and progress.  
* Deliverables for each phase will prioritize secure coding practices and maintainable system structure.

---

✅ Done\! Here's the **finalized version** of your References & Resources section with both the **HackTheBox Certified Bug Bounty Hunter** and the **IT and Cybersecurity Foundations \- Learn from Cybrary** course included.

---

# **10\. References & Resources (Finalized)** [↑ Top](#table-of-contents)

This section lists key technical references, tools, and learning resources that support the design and development of TradeStream. These resources provide guidance on industry standards, relevant technologies, and best practices.

---

## **10.1 Technical References** [↑ Section Top](#10-references--resources)

| Resource | Description |
| ----- | ----- |
| [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/) | Official guide for building Java Spring Boot applications. |
| [Spring Security Documentation](https://docs.spring.io/spring-security/reference/index.html) | Guidance on implementing secure authentication and authorization. |
| [Kafka Documentation](https://kafka.apache.org/documentation/) | Reference for setting up and using Apache Kafka. |
| [RabbitMQ Documentation](https://www.rabbitmq.com/documentation.html) | Reference for configuring and working with RabbitMQ. |
| [Docker Documentation](https://docs.docker.com/) | Official documentation for containerization using Docker. |
| [PostgreSQL Documentation](https://www.postgresql.org/docs/) | Relational database management system documentation. |
| [Redis Documentation](https://redis.io/docs/) | Documentation for in-memory caching and data store. |
| [JWT Introduction (Auth0)](https://auth0.com/learn/json-web-tokens) | Educational guide to understanding JSON Web Tokens. |
| [AWS Lightsail Documentation](https://lightsail.aws.amazon.com/ls/docs/en_us/articles) | Guide for deploying and managing services on AWS Lightsail. |

---

## **10.2 Learning & Best Practices** [↑ Section Top](#10-references--resources)

| Resource | Description |
| ----- | ----- |
| [OWASP Secure Coding Practices](https://owasp.org/www-project-secure-coding-practices-quick-reference-guide/) | Guidelines for secure software development. |
| [Microservices Patterns by Chris Richardson](https://microservices.io/) | Architectural patterns and best practices for microservices design. |
| [Docker for Java Developers (Baeldung)](https://www.baeldung.com/dockerizing-spring-boot-application) | Practical tutorial for containerizing Spring Boot apps. |
| [Event-Driven Architecture Overview (AWS)](https://aws.amazon.com/event-driven-architecture/) | Introduction to event-driven system design principles. |
| [HackTheBox Certified Bug Bounty Hunter (CBBH)](https://academy.hackthebox.com/preview/certifications/htb-certified-bug-bounty-hunter) | Hands‑on certification path covering web‑application pen‑testing and bug‑bounty methodologies. |
| [IT and Cybersecurity Foundations \- Cybrary](https://www.cybrary.it/course/it-and-cybersecurity-foundations/) | Foundational course providing a broad overview of essential IT and cybersecurity concepts. |

---

## **10.3 Personal Learning Resources** [↑ Section Top](#10-references--resources)

| Resource | Description |
| ----- | ----- |
| \[Spring Boot Course (e.g., Udemy/YouTube)\] | Ongoing practical learning for backend development. |
| \[Kafka or RabbitMQ Tutorials\] | Hands-on learning resources for message broker setup and usage. |
| \[AWS Lightsail Getting Started Guides\] | Step-by-step tutorials for initial cloud deployment. |

---

## **10.4 Future Exploration (Post-MVP)** [↑ Section Top](#10-references--resources)

* Kubernetes Documentation  
* Terraform Documentation  
* Prometheus & Grafana Resources  
* Advanced API Security Materials (e.g., OWASP API Security Top 10\)

---

---

#  **11. Appendix** [↑ Top](#table-of-contents)

This appendix provides supplementary information, definitions, and supporting details relevant to the TradeStream project. It serves as a quick reference for readers to better understand technical terms, acronyms, and concepts used throughout this document.

---

## **11.1 Acronyms & Abbreviations** [↑ Section Top](#11-appendix)

| Term  | Definition                                      |
| ----- | ----------------------------------------------- |
| API   | Application Programming Interface               |
| AWS   | Amazon Web Services                             |
| CI/CD | Continuous Integration / Continuous Deployment  |
| DB    | Database                                        |
| EDA   | Event-Driven Architecture                       |
| JWT   | JSON Web Token                                  |
| K8s   | Kubernetes                                      |
| MVP   | Minimum Viable Product                          |
| PRD   | Product Requirements Document                   |
| RBAC  | Role-Based Access Control                       |
| Redis | Remote Dictionary Server (In-memory data store) |
| SQL   | Structured Query Language                       |
| VCS   | Version Control System                          |

---

## **11.2 Definitions & Key Concepts** [↑ Section Top](#11-appendix)

| Term/Concept                        | Description                                                                                            |
| ----------------------------------- | ------------------------------------------------------------------------------------------------------ |
| **Microservice Architecture**       | Architectural style where the system is composed of small, independent, loosely coupled services.      |
| **Event-Driven Architecture (EDA)** | System design where components communicate via asynchronous events rather than direct calls.           |
| **Containerization**                | Packaging of software and its dependencies into isolated, portable containers using tools like Docker. |
| **Infrastructure as Code (IaC)**    | Managing and provisioning infrastructure through code (e.g., Terraform) rather than manual processes.  |
| **Observability**                   | The ability to understand a system’s internal state through metrics, logs, and monitoring tools.       |
| **Authentication Service**          | A dedicated service responsible for validating user credentials and issuing authentication tokens.     |
| **API Gateway**                     | A centralized entry point for client requests, enforcing security and routing to backend services.     |
| **Message Broker**                  | Middleware that enables decoupled, asynchronous communication between services via messages or events. |
| **JWT (JSON Web Token)**            | A compact, URL-safe token used for securely transmitting information between parties.                  |
| **Rate Limiting**                   | A technique used to control the number of requests a user or client can make to a service.             |

---

## **11.3 Tools & Technologies Summary** [↑ Section Top](#11-appendix)

| Technology                     | Purpose                                                   |
| ------------------------------ | --------------------------------------------------------- |
| Java Spring Boot               | Backend framework for building secure, scalable APIs      |
| Kafka/RabbitMQ                 | Message brokers for real-time, event-driven communication |
| PostgreSQL                     | Relational database for transactional data storage        |
| Redis                          | In-memory cache to improve performance                    |
| Docker                         | Containerization platform for isolated service deployment |
| AWS Lightsail                  | Cloud platform for hosting services                       |
| GitHub Actions                 | CI/CD pipeline for automated build, test, and deployment  |
| Kubernetes (Planned)           | Container orchestration for scaling and resilience        |
| Terraform (Planned)            | IaC for reproducible infrastructure provisioning          |
| Prometheus & Grafana (Planned) | Observability tooling for monitoring and metrics          |

---

## **11.4 Example User Credentials (MVP Testing)** [↑ Section Top](#11-appendix)

For demonstration and testing purposes only. These are non-production, hardcoded credentials intended for the MVP phase:

| Username | Password      | Notes                                                         |
| -------- | ------------- | ------------------------------------------------------------- |
| `admin`  | `password123` | Example user with basic access for testing API functionality. |

⚠️ **Note:** In a real-world production system, credentials would be securely stored, encrypted, and managed using appropriate security best practices.

---

## **11.5 Example API Request (Transaction Creation)** [↑ Section Top](#11-appendix)

**Endpoint:** `POST /transactions`
**Authorization:** Bearer Token (JWT) required
**Request Body Example:**

```json
{
  "amount": 250.00,
  "currency": "USD",
  "description": "Test transaction for MVP",
  "recipient": "Test Recipient"
}
```

**Example Successful Response:**

```json
{
  "id": "txn_123456",
  "status": "CREATED",
  "timestamp": "2025-07-06T12:34:56Z"
}
```

---

## **11.6 Future Considerations Checklist** [↑ Section Top](#11-appendix)

Planned enhancements for future project phases include:

✅ Kubernetes Deployment
✅ Terraform Infrastructure Management
✅ Prometheus & Grafana Observability
✅ Advanced API Security (e.g., Rate Limiting, RBAC)
✅ Full CI/CD Pipeline with Docker Image Publishing
✅ Market Data Generator Component

---

## **11.7 Relevant Learning Resources (Quick Links)** [↑ Section Top](#11-appendix)

* [Spring Boot Documentation](https://spring.io/projects/spring-boot)
* [Kafka Documentation](https://kafka.apache.org/documentation/)
* [RabbitMQ Documentation](https://www.rabbitmq.com/documentation.html)
* [Docker Documentation](https://docs.docker.com/)
* [PostgreSQL Documentation](https://www.postgresql.org/docs/)
* [Redis Documentation](https://redis.io/docs/)
* [JWT Introduction (Auth0)](https://auth0.com/learn/json-web-tokens/)
* [AWS Lightsail Documentation](https://lightsail.aws.amazon.com/ls/docs/en_us/articles/amazon-lightsail-docs)

---

## **11.8 Contact & Ownership** [↑ Section Top](#11-appendix)

**Project Owner:** Nick Efe Oni
**Role:** Author, Architect, and Developer of TradeStream
**Contact:** \[Your preferred contact method, e.g., email, GitHub profile]

---