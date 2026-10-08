# About
Application Name: Storefront
Prima
Primary Developer and Maintainer: Andros
## Full Stack
Database: postgresql
Database: postgresql

# FileSystem
.vscode                             - DO NOT MODIFY
database/                           - filestore for the storefront application
    ├── cache/                      - `.json` file system
    ├── items.csv                   - 
    └── information.json            - NoSQL database akin to `sqlite` that stores `Products`, `Users` & `Transactions`
src/
    ├── io.github.mimerme.core
    ├── io.github.mimerme.store
    ├── io.github.mimerme.users
    └── io.github.mimerme
        ├── Main.java               - Start the shell from here
        └── Utils.java              - Has only `static` methods in a `Utils` class that does: filesystem things, etc.