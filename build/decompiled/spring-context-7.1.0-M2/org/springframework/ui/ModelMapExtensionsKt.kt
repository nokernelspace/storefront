package org.springframework.ui

public operator fun ModelMap.set(attributeName: String, attributeValue: Any) {
   `$this$set`.addAttribute(attributeName, attributeValue)
}
