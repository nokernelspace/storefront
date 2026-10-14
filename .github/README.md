# Storefront
Completely CLI based as of now

- `sellingPrice = (buyingPrice * 0.5)+buyingPrice)`

`notes/` - are notes for AI agents

# WIP
- testing transaction
- add profit checking for admins' in `product category`

## Bugs
	- [x] User's not saving and loading properly?
	- [x] Fix quantity always restarting from 1. Because schema is checking for equality using .quantity field 
	- [x] Product.id is always randomly generated. Buying doesn't work
	- [x] Switch key of StoreInfo.products to use .id

	- [ ] Implement .hashCode() for `Transaction`
	- [ ] don't write to .quantity to persistent store unless the user `checkout`

## Admin Functions/Features
	- [x] Generate `Products` from `Items` 
	- [x] Implement `product` -> `sort`	-> `ctegory`
	- [x] Implement `product` -> `sort`	-> `price`
	- [x] Implement `product` -> `list`	
	- [x] Implement `buy`	
	- [x] Implement history
		- [x] On every `checkout` write to the `history: []` field and exit the program
	- [x] Implement `checkout`	
		- [x] Add transaction
	
    - IMPORTANT [x]: Check Total Amount Spent on Products over all time
    	- `product` -> `total`
    - IMPORTANT [ ]: Check the profit category wise
    	- `product` -> `category`
    - List products in the store
        - Columns:
            - productId
            - product name
            - category
            - quantity
            - price
    - search the product by using productId
            - <s>[ ] .contains</s>
            - <s>[ ] String Similarity</s>
            - ID is a random string don't do this 
    - List products by category
    - List products by name
            - [x] .contains
            - [ ] String Similarity
    - [ ] List products by individual columns [?!]

## Customer Functions/Features
    - Login
        [x] Test Login via `ls`
    - Registration
        - Add 100 `Super Coins` on new user registration
        [x] Test create via `ls`
        [x] Test persistence
    - Browse Products
        - [x] create a `products` command
        - [x] Sort Prices Low to High
        - [ ] (optional) Sort Prices High to Low
        - [x] Filter products by category


## Technical Requirements
[x] Cache product details in memory using a `Map<K,V>`
[x] Create a `StoreInformation` class to wrap the SQL queries / writes to persistent data store
[x] Display menu options with `help` and quit program with `exit`
[ ] Create a custom exception for handling improper input. Need to do (currently everything is a usages. I like my way better)
[ ] Keep everything on the command line. No separate drivers. We need a separate thread for each user
- Port stuff to Java 8+


## Additional Features
```
main() {
    splits[0] == "ls"       // List users
    splits[0] == "login"    // Admin is a user
    splits[0] == "exit"
    splits[0] == "help"
}
```


## TODO
[x] Setup `Scanner()` and parse command line first
[x] Work on User Serialization To Individual Files
[x] Work on Product Serialization To Individual Files
[ ] Create a directory for the persistent data store

[ ] (optional) Use SQL if have time
