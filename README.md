# relax-graph

Simple graph builder.

- The Graph is copy and refined from Dolphin Scheduler, 
- And render mermaid flowchart diagram as string, copy and view the diagram with [https://mermaid.live](https://mermaid.live).

```mermaid
 flowchart LR
    1([1]) --> 2([2])
    2([2]) --> 5([5])
    3([3]) --> 5([5])
    4([4]) --> 6([6])
    5([5]) --> 6([6])
    6([6]) --> 7([7])
```

## Release

- Snapshot: `mvn clean deploy`
- Release: `mvn clean package source:jar gpg:sign install:install deploy:deploy`
