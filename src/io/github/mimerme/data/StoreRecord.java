package io.github.mimerme.data;

import java.util.ArrayList;
import java.util.HashMap;

import io.github.mimerme.storefront.Product;
import io.github.mimerme.storefront.Transaction;
import io.github.mimerme.users.User;

/// Just a wrapper for GSON
public class StoreRecord {
	public HashMap<String, Product> products = new HashMap();
	public ArrayList<User> users = new ArrayList<>();
	public ArrayList<Transaction> transactions = new ArrayList<>();
}
