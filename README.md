# std_bot_firefox_plugin
This is my @std_bot_cpp as Firefox plugin.

This repo contains of 2 programs.

## The plugin

You can add it to your Firefox and it should work as intended.

Currently it only works for the new design and only if your comment is sent via Markdown Editor. You can edit your comments, new links will be set, old ones will not be linked again.

The code is a mess, because I don't know JS. If you like to contribute to clean it up or make it run in fancy editor or old Reddit, feel free.

## CppReferenceIndexer

Its a Java program that searches cppreference.com for functions/classes/objects (entities) and stores them with their link. This program only needs to run if the site adds entities to their site.

It also has an internal wait of 5s per access and caches pages in memory. Please don't remove it, please don't DOS cppreference.com. Last time I ran it, it took about an hour, you have that time.

The data created with this program needs to be put into Plugin/content-script.js. Simply replace the existing data at the top.

## QA

### A function/class/object in my comment wasn't linked

There are 2 typical reasons for this:

- Your entity doesn't appear on cppreference.com in a way my indexer found it. I start with the symbol index of "std" and work my way up. If the entity wasn't found but has a site you can add it by hand.
- Some functions have different sites for different overload. For example *std::chrono::from_stream<>()* has 13 different overloads with each having their own site. I simply don't know which to link in such cases.

### What about entities in code/quotes?

There is code to add a list of used entities from code blocks/quotes below your comment. Its currently deactivated because I'm not sure if I like it. Usually links to entities are only good if you actually talk about them. Inline code is ignored, they don't mix well with hyperlinks.

### When switching from Rich Text Editor to Markdown Editor Reddit escapes characters such as '[', ']', '_', do I have to un-escape them before sending?

No, the plugin does that already. You should be able to just switch and send it directly.

### I sent the comment accidentally in Rich Text Editor so nothing was linked. Do I have to delete the comment and re-create it for the plugin to work?

No, simply edit (with or without changes) and sent again via Markdown Editor.

### I like this

Thanks, feel free to contribute.
