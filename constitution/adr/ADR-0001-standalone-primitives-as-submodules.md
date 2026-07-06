---
id: ADR-0001
title: Standalone primitives as submodules
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

Every standalone, reusable primitive we build is developed in its own repository
and consumed by the harness as a git submodule, rather than being absorbed into a
harness-internal directory. This keeps primitives independently versioned, testable,
and reusable beyond the harness, and preserves the harness as a thin wrapper that
consumes primitives instead of owning them.

## Rule

Every standalone, reusable primitive lives in its own repo and is consumed by the
harness as a git submodule — never absorbed into a harness-internal directory.
