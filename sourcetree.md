src/io/github/mimerme
├── Shell.java                              [Driver] Admin console
├── Storefront.java                         [Driver] HTTP Server powered by `spring-web`
├── YarosUtils.java                         [Core] Vanilla Java CoreUtils. Has only `static` methods
├── YarosSpringUtils.java                   [Core] Spring CoreUtils. Has only `static` methods
├── Vanilla.java                            [Driver] HTTP Server powered by Java's built-in HTTPServer (`com.sun.net.httpserver.HttpServer`)
├── core
│   ├── Item.java                           Backend facing marketable entity
│   ├── Product.java                        Frontend facing marketable entity
│   ├── ShoppingCart.java                   [???] Figure out why this is here
│   └── Transaction.java                    Serialized by GSON. User class. Abstracted @ runtime for devops teams configuring.
├── store
│   ├── Category.java                       Enumerable type. Idk. This is just more convenient than having a nested class since java doesn't support multiple classes in one file lol
│   ├── StoreInformation.java               Primary worker for interfacing with the filesystem
│   └── StoreRecord.java                    [!] Serialized by GSON. The primary GSON class that gets serialized before it gets duped from memory onto the filesystem
└── users                                   Directory for hardcoded users
    └── User.java                           Serialized by GSON. User class. Abstracted @ runtime for devops teams configuring. 

4 directories, 13 files
