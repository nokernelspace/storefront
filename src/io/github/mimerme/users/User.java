package io.github.mimerme.users;

import java.util.Random;
import java.util.concurrent.RunnableFuture;

import io.github.mimerme.core.ShoppingCart;

public class User {
	public String id;
	public String username;
	public String email;
	public String password;
	// Which number type? Remove after
	public double superCoins;

	public boolean isAdmin = false;
	
	
	public transient Thread thread;
	
	/// Carts are emptied on server restart
	public transient ShoppingCart cart = new ShoppingCart();

	public User() {
		
	}
	
	public User(String username, String email, String password) {
		this.id = generateUUID();
		this.username = username;
		this.email = email;
		this.password = password;
		this.superCoins = 100;
		this.isAdmin = false;
	}

	/// Should only be used once in the code base
	public User(String username, String email, String password, double superCoins, boolean isAdmin) {
		this.username = username;
		this.email = email;
		this.password = password;
		this.superCoins = superCoins;
		this.isAdmin = isAdmin;
	}

	static String generateUUID() {
		String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
		Random random = new Random();

		int length = 10;

		StringBuilder sb = new StringBuilder(length);

		// 2. Build the string character by character
		for (int i = 0; i < length; i++) {
			int randomIndex = random.nextInt(characters.length());
			sb.append(characters.charAt(randomIndex));
		}

		String randomString = sb.toString();
		return randomString;
	}

	public void login() {
		thread = new Thread(() -> {
			// TODO: Move user functions here
		});
		thread.start();
	}
}