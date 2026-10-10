# Contributing to Anxin Home Service Platform

Thank you for your interest in contributing!

## Project scope

This repository implements a housekeeping booking and service-management platform with Spring Cloud and Spring AI. Contributions should preserve its business boundaries, authorization rules and confirmation flow.

- Keep business writes inside the owning business microservice.
- Keep model Tool access narrower than direct administrator access.
- Require backend validation and user confirmation for AI-initiated mutations.
- Do not commit API keys, JWT secrets, database passwords or local credential files.

## What we accept

- Bug fixes with a linked issue
- Dependency upgrades (Spring Boot, Spring Cloud ...)
- Documentation improvements
- Features discussed and agreed upon in an issue first

## What we do NOT accept

- Changes that introduce an unrelated business domain or bypass the housekeeping service boundaries
- AI operations that bypass authentication, authorization or confirmation
- PRs that duplicate already-open contributions
- Changes without tests

## How to contribute

1. **Open an issue first** to discuss the change you want to make.
2. Fork the repository and create a branch: `git checkout -b bugfix/my-bug-fix`
3. Make your changes and add tests if applicable.
4. Run the test suite: `./mvnw test`
5. Open a Pull Request using the provided template and fill it out completely.

## Reporting a bug

Open an issue with:
- A clear description of the bug
- Steps to reproduce
- Expected vs actual behaviour
- Spring Boot / Java version
