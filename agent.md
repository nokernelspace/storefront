# About
Application Name: Storefront
Application Description: 
Primary Developer and Maintainer: Andros
    Pseudonyms: Mimerme, nokernelspace, BunNet, Yaros, Stella, REED, lyoko, xana

# Terminology
**Core** : code meant to be used across projects
**Driver** : where the `static void main(String[] args)` drivers are
**?** : I don't know. Please explain if possible and convenient. When explaining use all caps.
**!** : important at the time

# Stack Options
Due to Spring there are many options for an HTTP Server.

## Current Configuration
Dependency Management : Maven

# FileSystem
.vscode                             - DO NOT MODIFY
database/                           - filestore for the storefront application
    ├── cache/                      - [?] `.json` file system
    ├── items.csv                   - 
    └── information.json            - NoSQL database akin to `sqlite` that stores `Products`, `Users` & `Transactions`
src/
    ├── io.github.mimerme.core
    ├── io.github.mimerme.store
    ├── io.github.mimerme.users
    └── io.github.mimerme
        ├── Main.java               - Start the shell from here
        └── Utils.java              -  in a `Utils` class that does: filesystem things, etc.



├── README.md
├── agent.md
├── bin
│   └── module-info.class
├── database
│   ├── cache
│   │   ├── information.json
│   │   └── items.csv
│   ├── information.json
│   ├── information_copy.json
│   ├── information_old.json
│   └── items.csv
├── filetree.txt
├── pom.xml
├── sourcetree.md
├── src
│   ├── io
│   │   └── github
│   │       └── mimerme
│   │           ├── Shell.java
│   │           ├── Storefront.java
│   │           ├── Vanilla.java
│   │           ├── YarosSpringUtils.java
│   │           ├── YarosUtils.java
│   │           ├── core
│   │           │   ├── Item.java
│   │           │   ├── Product.java
│   │           │   ├── ShoppingCart.java
│   │           │   └── Transaction.java
│   │           ├── store
│   │           │   ├── Category.java
│   │           │   ├── StoreInformation.java
│   │           │   └── StoreRecord.java
│   │           └── users
│   │               └── User.java
│   └── test
│       └── java
├── storefront.code-workspace
└── target
    ├── classes
    │   ├── io
    │   │   └── github
    │   │       └── mimerme
    │   │           ├── Shell.class
    │   │           ├── Storefront.class
    │   │           ├── Vanilla$HelloHandler.class
    │   │           ├── Vanilla.class
    │   │           ├── YarosSpringUtils.class
    │   │           ├── YarosUtils.class
    │   │           ├── core
    │   │           │   ├── Item.class
    │   │           │   ├── Product.class
    │   │           │   ├── ShoppingCart.class
    │   │           │   └── Transaction.class
    │   │           ├── store
    │   │           │   ├── Category.class
    │   │           │   ├── StoreInformation.class
    │   │           │   └── StoreRecord.class
    │   │           └── users
    │   │               └── User.class
    │   └── test
    │       └── java
    ├── generated-sources
    │   └── annotations
    ├── maven-status
    │   └── maven-compiler-plugin
    │       └── compile
    │           └── default-compile
    │               ├── createdFiles.lst
    │               └── inputFiles.lst
    └── test-classes

30 directories, 42 files