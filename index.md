# CS 499 Computer Science Capstone ePortfolio

## Professional Self-Assessment

Welcome to my ePortfolio. This portfolio represents the culmination of my journey through the Computer Science program at Southern New Hampshire University. Throughout this program, I have developed skills in software engineering, database management, algorithm design, and security implementation that prepare me for a career in software development.

---

## Code Review

Before enhancing my artifacts, I conducted a code review to identify areas for improvement in security, functionality, and design.

<iframe width="560" height="315" src="https://www.youtube.com/embed/YOUR_VIDEO_ID" frameborder="0" allowfullscreen></iframe>

*Replace YOUR_VIDEO_ID with your actual YouTube video ID*

---

## Artifact One: Software Design and Engineering

**Artifact:** Thermostat.py - Embedded Systems Temperature Controller

This artifact demonstrates my skills in software design and engineering through a Python-based thermostat control system originally developed in CS 350.

[View Thermostat.py](Thermostat.py)

### Enhancements Made:
- Improved code structure and modularity
- Added input validation and error handling
- Implemented design patterns for maintainability

---

## Artifact Two: Algorithms and Data Structures

**Artifact:** DataGridActivity.java - Android Data Display Component

This artifact showcases my understanding of algorithms and data structures through efficient data handling and display logic.

[View DataGridActivity.java](DataGridActivity.java)

### Enhancements Made:
- Optimized data retrieval algorithms
- Improved sorting and filtering functionality
- Enhanced memory efficiency

---

## Artifact Three: Databases

**Artifact:** DatabaseHelper.java - Secure SQLite Database Manager

This artifact demonstrates my skills in database design and security implementation for an Android inventory management application originally developed in CS 360.

[View DatabaseHelper.java](DatabaseHelper.java)

### Enhancements Made:
- Normalized schema to Third Normal Form (3NF) with categories table
- Implemented BCrypt password hashing (no plain text storage)
- Added SQL parameterization to prevent injection attacks
- Wrapped operations in try-catch-finally for defensive programming

### Narrative:

The artifact is DatabaseHelper.java, the data access layer for an Android inventory management application I developed during CS 360: Mobile Architecture and Programming in 2024. This class manages all SQLite database operations including user authentication and inventory CRUD functionality.

I selected this artifact because it demonstrates growth in database design and security implementation. I improved it by normalizing the schema to Third Normal Form with a separate categories table linked via foreign key, implementing BCrypt password hashing to eliminate plain text password storage, ensuring all queries use SQL parameterization to prevent injection attacks, and adding try-catch-finally blocks with proper cursor cleanup for defensive programming.

These enhancements meet the outcomes I planned in Module One. For CS-499-04, I applied well-founded techniques including BCrypt hashing, SQL parameterization, and 3NF normalization. For CS-499-05, I developed a security mindset by anticipating SQL injection, credential theft, and timing attacks in my design decisions. All planned improvements were completed without changes to my outcome-coverage plans.

Through this process, I learned why best practices matter beyond just knowing what they are. Implementing BCrypt taught me the security-performance tradeoff of work factors, and schema refactoring required thinking through real usage scenarios rather than applying rules mechanically. The main challenge was maintaining backward compatibility, which I solved by creating deprecated wrapper methods that delegate to the new implementations.

---

## Contact

- **GitHub:** [pshacker01](https://github.com/pshacker01)
- **LinkedIn:** [Your LinkedIn URL]
- **Email:** [Your Email]
