---
id: ADR-0003
title: Primitive repo shape
category: architecture
date: 2026-07-05
status: accepted
---

## Context and Problem Statement

Established at project bootstrap by `constitution init`.

## Considered Options

- Adopt this founding principle
- Leave the convention implicit

## Decision Outcome

Each primitive repository follows a consistent shape so it is self-contained and
adoptable across agent frameworks, and so its design intent travels with the code.

## Rule

Each primitive ships a framework-neutral core, agent-agnostic skills, thin
per-framework adapters, an in-repo spec + implementation plan, and an MIT license.
