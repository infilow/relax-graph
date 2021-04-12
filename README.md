# relax-graph

Simple graph builder.

- The Graph is copy and refined from Dolphin Scheduler, 
- And Render with ascii-graphs which depends on scala 2.12.

```
       ┌─┐    ┌─┐              
       │ │    │ │              
    ┌─>│2├───>│ │     ┌─┐      
┌─┐ │  │ │    │5│     │ │   ┌─┐
│ │ │  └─┘ ┌─>│ ├────>│ │   │ │
│1├─┘      │  │ │     │6├──>│7│
│ │    ┌─┐ │  └─┘ ┌──>│ │   │ │
└─┘    │ │ │      │   │ │   └─┘
       │3├─┘  ┌─┐ │   └─┘      
       │ │    │ │ │            
       └─┘    │4├─┘            
              │ │              
              └─┘ 
```

## Release

- Snapshot: `mvn clean deploy`
- Release: `mvn clean package source:jar gpg:sign install:install deploy:deploy`
