package io.github.mimerme.store;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import io.github.mimerme.core.Product;
import io.github.mimerme.core.Transaction;
import io.github.mimerme.users.User;

/// Just a wrapper for GSON
public class StoreRecord {
	public HashMap<String, Product> products = new HashMap();
	public ArrayList<User> users = new ArrayList<>();
	public ArrayList<Transaction> transactions = new ArrayList<>();
}
