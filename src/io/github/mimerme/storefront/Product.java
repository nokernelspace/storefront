package io.github.mimerme.storefront;

import io.github.mimerme.data.Category;

// Seller's perspective
// NOTE: 'with' keyword in Dart?
public class Product {
	 public String id;
	 public String name;
	 public double sellingPrice;
	 public int quantity;
	 public Category category;
	 
	 public Product() {
		 
	 }
	 
	 public Product(String id, String name, double sellingPrice, int quantity, Category category) {
		 this.id = id;
		 this.name = name;
		 this.sellingPrice = sellingPrice;
		 this.quantity = quantity;
		 this.category = category;
		 // TODO: Check for UUID collisions
	 }
	 

	 
	 @Override
	 public boolean equals(Object p) {
		Product cast = (Product) p;
		
		/// Don't check ID so we can compare schema properly
		return cast.name.equals(this.name) 
				&& cast.sellingPrice == this.sellingPrice;
	 }
	 
	 @Override
	 public Object clone() {
		 return new Product(
				 this.id,
				 this.name,
				 this.sellingPrice,
				 this.quantity,
				 this.category
		);
	 }
}