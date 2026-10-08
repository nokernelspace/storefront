package io.github.mimerme.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.github.mimerme.store.StoreInformation;


public class ShoppingCart {
	public ArrayList<Product> contents = new ArrayList();
	
	///public HashMap<String, Product> contents = new HashMap<>();

	
	public void buy(String productId, int quantity) {
		Product clone = (Product) StoreInformation.INSTANCE.data.products.get(productId).clone();
		clone.quantity = quantity;
		
		/// TODO: implement hashmap
		
		contents.add(
			clone
		);
	}
}