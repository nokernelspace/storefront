package io.github.mimerme.storefront;

import java.util.List;

import io.github.mimerme.users.User;

public class Transaction {
	public String buyerUsername;
	public List<Product> products;
	
	/// Just in case
	public Transaction() {
		
	}
	
	public Transaction(User user, List<Product> products) {
		this.buyerUsername = user.username;
		this.products = products;
	}
}
