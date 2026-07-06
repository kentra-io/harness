---
id: ADR-0002
title: Neutral mechanism, branded methodology
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

Primitives are built as framework-neutral, unbranded mechanisms so they remain
reusable beyond kentra; branding is confined to the composition layer. This keeps
the reusable core license-clean and adoptable by others, while kentra's opinionated
methodology composes those neutral parts.

## Rule

Primitive repos stay framework-neutral, unbranded, and MIT-licensed (reusable beyond
kentra). The `kentra-` prefix appears only at the branded layer (schemas, umbrella
methodology).
