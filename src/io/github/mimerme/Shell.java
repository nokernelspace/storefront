package io.github.mimerme;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Pattern;

import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.mimerme.core.Item;
import io.github.mimerme.core.Product;
import io.github.mimerme.core.Transaction;
import io.github.mimerme.store.Category;
import io.github.mimerme.store.StoreInformation;
import io.github.mimerme.users.User;

@Configuration
public class Shell {
	public static List<Product> searchProduct(String search) {
		List<Product> productMatches = StoreInformation.INSTANCE.data.products
				.values()
				.stream()
				.filter((product) -> {
					/// IMPORTANT
					return product.name.contains(search) || search.contains(product.name);
				})
				.toList();

		if (productMatches.size() == 0) {
			// Check for exact product ID
			productMatches = StoreInformation.INSTANCE.data.products
					.values()
					.stream()
					.filter((product) -> {
						return product.id.equals(search);
					})
					.toList();

			if (productMatches.size() == 0) {
				System.out.printf("No product matches for query \"%s\"\n", search);
				return new ArrayList();
			}
		}
		return productMatches;
	}

	/// Returns true if the schema matches the persistent file store
	/// Returns false if a reset is required
	public static boolean checkProductSchema(Map<String, Product> newSchema) {
		// Check for equalities. Do not compare K, .id or .quantity
		if (newSchema.values().size() != StoreInformation.INSTANCE.data.products.values().size()) {
			System.out.println("Schema size missmatch");
			return false;
		}
		
		for (int i = 0; i < newSchema.values().size(); i++) {
			Product a = (Product) newSchema.values().toArray()[i];
			Product b = (Product) StoreInformation.INSTANCE.data.products.values().toArray()[i];
			
			if (a.equals(b)) {
				continue;
			}
			else {
				return false;
			}
		}
		
		return true;
	}

 	public static void main(String[] args) {
		SpringApplication.run(Shell.class);
	}

	@Bean
	public static Object main() {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			System.out.println("Saving to filesystem...");
			StoreInformation.INSTANCE.store();
		}));
		User admin = new User("admin", "", "password123", Double.MAX_VALUE, true);
		Scanner scanner = new Scanner(System.in);

		System.out.println("Initializing Products...");
		/// TODO: exception here
		StoreInformation.INSTANCE.load();

		System.out.println("Initializing Items...");
		// TODO: implement via filepath
		ArrayList<Item> items = Item.fromFile();

		System.out.println("Generating Expected Product Schema...");
		HashMap<String, Product> newSchema = new HashMap<String, Product>();
		for (Item item : items) {
			newSchema.put(
					item.id,
					new Product(
							item.id,
							item.name,
							((item.buyingPrice * 0.5) + item.buyingPrice),
							1,
							item.category
							)
					);
		}

		if (!checkProductSchema(newSchema)) {
			System.out.println("There are differences in the Product schema\nMigrating...");
			StoreInformation.INSTANCE.data.products = newSchema;
		}
		
		System.out.println("Finished.");

		User currentUser = admin;

		while (true) {
			System.out.printf(currentUser == null ? ">>> " : currentUser.username + ">>> ");
			String line = scanner.nextLine();
			String[] splits = line.split(" ");

			// No-Auth commands
			if (splits[0].equals("ls") || splits[0].equals("list")) {
				System.out.printf("cwd: %s\n", System.getProperty("user.dir"));

				// Header
				//				System.out.printf("%-15s | %-15s | %-10s%n", "ID", "Username", "E-Mail\n");
				System.out.printf("%15s | %15s | %10s\n", "ID", "Username", "E-Mail");
				System.out.println("-------------------------------------------------");

				/// Print
				for (User user : StoreInformation.INSTANCE.data.users) {
					System.out.printf("%15s | %15s | %10s\n", user.id, user.username, user.email);
					//					System.out.printf("%-15s | %-15s | $%-9d%n\n", user.id, user.username, user.email);
				}
			} else if (splits[0].equals("pwd") || splits[0].equals("cwd")) {
				System.out.println(YarosUtils.cwd());
			} else if (splits[0].equals("login")) {
				if (splits.length < 2 + 1) {
					System.out.println("USAGE: login <USERNAME/EMAIL> <PASSWORD>");
					continue;
				}

				// Check login by username
				for (User user : StoreInformation.INSTANCE.data.users) {
					if (user.username.equals(splits[1])) {
						if (user.password.equals(splits[2])) {
							// Success !
							currentUser = user;
							System.out.println(String.format("Now logged in as %s", user.username));
							break;
						}
						System.out.println("USAGE: login <USERNAME/EMAIL> <PASSWORD>");
					}
				}

				// Check login by email
				for (User user : StoreInformation.INSTANCE.data.users) {
					if (user.email.equals(splits[1])) {
						if (user.password.equals(splits[2])) {
							// Success !
							currentUser = user;
							System.out.println(String.format("Now logged in as %s", user.username));
						}
						System.out.println("USAGE: login <USERNAME/EMAIL> <PASSWORD>");
					}
				}

				currentUser.login();
			}  else if (splits[0].equals("exit")) {
				System.exit(0);
			} else if (splits[0].equals("help")) {
				System.out.println("");
				System.out.println("Commands");
				System.out.printf("%-15s : %s", "login", "Login as a user using username/email & password\n");
				System.out.printf("%-15s : %s", "create", "Create a new user using username, email, & password\n");
				System.out.printf("%-15s : %s", "list", "List Users\n");
				System.out.printf("%-15s : %s", "exit", "Exit the shell\n");
				
				
				System.out.println("");
				System.out.println("User Commands");
				System.out.printf("%-15s : %s\n", "product", "List and sort available products");
				System.out.printf("%-15s : %s\n", "buy", "Buy a product");
				System.out.printf("%-15s : %s\n", "checkout", "Pay for the products in your cart");

				if (currentUser != null && currentUser.isAdmin) {
					/// Print super-duper secret admin features/functions
					System.out.println("");
					System.out.println("Administrator Commands");
					System.out.printf("%-15s %-15s : %s\n", "product", "total", "Check overall spending by time");
					System.out.printf("%-15s %-15s : %s\n", "product", "category", "Check overall spending by category");
				}
			} else if (splits[0].equals("create")) {
				if (splits.length < 3 + 1) {
					System.out.println("USAGE: create <USERNAME> <EMAIL> <PASSWORD>");
					continue;
				}

				String username = splits[1];
				String email = splits[2];
				Pattern pattern = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
				if (!pattern.matcher(email).matches()) {
					System.out.println(String.format("Invalid Email \"%s\"", email));
					continue;
				}

				String password = splits[3];
				List<User> existing = StoreInformation.INSTANCE.data.users
						.stream()
						.filter((user) -> user.username.equals(username) || user.email.equals(email))
						.toList();

				if (existing.size() == 0) {
					StoreInformation.INSTANCE.data.users.add(new User(username, email, password));
				}
				else {
					System.out.println("That user already exists");
				}

			} else if (splits[0].equals("product")) {
				if (splits.length >= 2) {
					if (splits[1].equals("list")) {
						if (splits.length >= 4) {
							if (splits[2].equals("category")) {
								System.out.println("Products (by Category)");
								System.out.printf("%-15s | %-15s | %-15s | %-15s | %-15s\n", "productId", "Product Name", "Category", "Quantity", "Price");
								for (Product product : StoreInformation.INSTANCE.data.products.values()) {
									try {
										if (product.category.equals(Category.valueOf(splits[3].trim().toUpperCase()))) {
											System.out.printf("%-15s | %-15s | %-15s | %-15s | %-15s\n", product.id, product.name, product.category, product.quantity, product.sellingPrice);
										}
									}
									catch(IllegalArgumentException e) {
										System.out.printf("Invalid Category %s\n", splits[3].trim().toUpperCase());
									}
									catch (Exception e) {
										e.printStackTrace();
									}
								}
							}
							else if (splits[2].equals("name")) {
								System.out.println("Products (by Name)");
								System.out.printf("%-15s | %-15s | %-15s | %-15s | %-15s", "productId", "Product Name", "Category", "Quantity", "Price");
								for (Product product : StoreInformation.INSTANCE.data.products.values()) {
									if (product.name.contains(splits[3])) {
										System.out.printf("%-15s | %-15s | %-15s | %-15s | %-15s\n", product.id, product.name, product.category, product.quantity, product.sellingPrice);
									}
								}
							}
							else {
								System.out.println("Usage: product list <category/name> [...]");
							}
						}

						else {
							System.out.println("Products (by Price [Low to High])");
							System.out.printf("%-15s | %-15s | %-15s | %-15s | %-15s\n", "productId", "Product Name", "Category", "Quantity", "Price");
							StoreInformation.INSTANCE.data.products
							.values()
							.stream()
							.sorted(Comparator.comparingDouble(p -> p.sellingPrice))
							.forEach((product) -> {
								System.out.printf("%-15s | %-15s | %-15s | %-15s | %-15s\n", product.id, product.name, product.category, product.quantity, product.sellingPrice);
							});
						}

						// TODO: IMPORTANT
						//						if (currentUser.isAdmin) { 
						//						}
					}
					else if (splits[1].equals("stock")) {
						if (currentUser != null && currentUser.isAdmin) {
							if (splits.length >= 3) {
								try {
									int stock = Integer.parseInt(splits[2]);
									if (stock < 0) {
										System.out.println("Integer cannot be negative");
										continue;
									}
									
									if (splits.length >= 4) {
										String id = splits[3];
										Product product = StoreInformation.INSTANCE.data.products.get(id);
										product.quantity += stock;
										continue;
									}
									else {
										System.out.println("Usage: product stock <QUANTITY> <ID>");
										continue;
									}
								}
								catch (IllegalArgumentException e) {
									System.out.printf("Error parsing Integer: \"%s\"\n", splits[2]);
								}
							}
						}
						else {
							System.out.println("This is an administrator only command. This incident will be logged");
						}
					}
					else if (splits[1].equals("total")) {
						if (currentUser.isAdmin) {
							double total = 0.0;
							for (Transaction transaction : StoreInformation.INSTANCE.data.transactions) {
								for (Product product : transaction.products) {
									total += product.quantity * product.sellingPrice;
								}
							}
						
							if (total != 0.0) {
								System.out.printf("Total Spent: %.2f\n", total);
							}
							else {
								System.out.println("Nobody has spent anything (╯°□°)╯");
							}
						}
						else {
							System.out.println("This is an administrator only command");
							continue;
						}
					}
					else if (splits[1].equals("category")) {
						if (currentUser.isAdmin) {
							HashSet<Transaction> products = new HashSet<>();
							
							for (Category category : Category.values()) {
								double category_total = 0.0;
								for (Transaction transaction : StoreInformation.INSTANCE.data.transactions) {
									if (products.contains(transaction)) {
										// Skip if already processed
										continue;
									}
									
									for (Product product : transaction.products) {
										/// Process matching categories
										if (product.category == category) {
											category_total += product.quantity * product.sellingPrice;
											/// Setup skip
											products.add(transaction);
										}
									}
							
								}
								System.out.printf("%-15s | %-15s\n", category, category_total);
							}
						
//							for (Map.Entry<Category, Double> entry : products.entrySet()) {
//							}
						}
						else {
							System.out.println("This is an administrator only command");
							continue;
						}
					}
					else {
						System.out.println("Usage: product <list/stock/total/category> [...]");
					}
				}
				else {
					System.out.println("Usage: product <list/stock/total/category> [...]");
				}
			} else if (splits[0].equals("buy")) {
				if (currentUser == null) {
					System.out.println("You need to be logged in to do that");
					continue;
				}


				if (splits.length < 2 + 1) {
					System.out.println("Usage: buy <PRODUCT_ID/PRODUCT_NAME> <QUANTITY>");
					continue;
				}

				String search = splits[1];
				int quantity;
				try {
					quantity = Integer.parseInt(splits[2]);
				} catch (Exception e) {
					System.out.printf("Failed to print \"%s\" as Integer \n", splits[2]);
					continue;
				}
				
				List<Product> products = searchProduct(search);
				
				if (products.size() == 0) {
					System.out.printf("Product not found using '%s'\n", search);
					continue;
				}
				// Actually do the buying
				else {
					if (products.size() == 1) {
						Product product = products.get(0);
						/// Handle not enough quantity
						if (product.quantity > 0) {
							/// Handle quantity deduction
							product.quantity -= quantity;
						
							/// Clamp
							if (product.quantity <= 0) {
								product.quantity = 0;
							}
							currentUser.cart.buy(product.id, quantity);
						}
						else {
							System.out.println("Not enough quantity. Please wait for a restock");
						}
						continue;
					}
					else {
						System.out.println("Please refine your search query");
						continue;
					}
				}
		
				
			} else if (splits[0].equals("checkout")) {
				if (currentUser == null) {
					System.out.println("You need to be logged in to do that");
					continue;
				}

				if (currentUser.isAdmin) {
					System.out.println("Would you like to commit fraud?");
				}
				
				if (currentUser.cart.contents.size() == 0) {
					System.out.println("Cart cannot be empty. Add items with the `buy` command");
				}
			
				double sum = 0;
				for (Product p : currentUser.cart.contents) {
					sum += p.quantity * p.sellingPrice;
				}
				System.out.printf("Your total is $%.2f%n", sum);
				StoreInformation.INSTANCE.data.transactions.add(
						new Transaction(
								currentUser,
								currentUser.cart.contents
						)
				);
				System.out.println("Have a nice day ☜(⌒▽⌒)☞");
				System.exit(0);
				/// TODO: handle payment processing
				
				/// TODO: Log to history
				/// 
				/// 
				/// 
			} else {
				System.out.println("Unrecognized command");
			}

			// User-commands

			// Admincommands
		}

	}
}
