package org.springframework.boot.env;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.YamlProcessor;
import org.springframework.boot.origin.Origin;
import org.springframework.boot.origin.OriginTrackedValue;
import org.springframework.boot.origin.TextResourceOrigin;
import org.springframework.core.io.Resource;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.BaseConstructor;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

class OriginTrackedYamlLoader extends YamlProcessor {
   private final Resource resource;

   OriginTrackedYamlLoader(Resource resource) {
      this.resource = resource;
      this.setResources(new Resource[]{resource});
   }

   protected Yaml createYaml() {
      LoaderOptions loaderOptions = new LoaderOptions();
      loaderOptions.setAllowDuplicateKeys(false);
      loaderOptions.setMaxAliasesForCollections(Integer.MAX_VALUE);
      loaderOptions.setAllowRecursiveKeys(true);
      loaderOptions.setCodePointLimit(Integer.MAX_VALUE);
      return this.createYaml(loaderOptions);
   }

   private Yaml createYaml(LoaderOptions loaderOptions) {
      BaseConstructor constructor = new OriginTrackedYamlLoader.OriginTrackingConstructor(loaderOptions);
      DumperOptions dumperOptions = new DumperOptions();
      Representer representer = new Representer(dumperOptions);
      OriginTrackedYamlLoader.NoTimestampResolver resolver = new OriginTrackedYamlLoader.NoTimestampResolver();
      return new Yaml(constructor, representer, dumperOptions, loaderOptions, resolver);
   }

   List<Map<String, Object>> load() {
      List<Map<String, Object>> result = new ArrayList<>();
      this.process((properties, map) -> result.add(this.getFlattenedMap(map, true, "")));
      return result;
   }

   private static class KeyScalarNode extends ScalarNode {
      KeyScalarNode(ScalarNode node) {
         super(node.getTag(), node.getValue(), node.getStartMark(), node.getEndMark(), node.getScalarStyle());
      }

      static NodeTuple get(NodeTuple nodeTuple) {
         Node keyNode = nodeTuple.getKeyNode();
         Node valueNode = nodeTuple.getValueNode();
         return new NodeTuple(get(keyNode), valueNode);
      }

      private static Node get(Node node) {
         return (Node)(node instanceof ScalarNode scalarNode ? new OriginTrackedYamlLoader.KeyScalarNode(scalarNode) : node);
      }
   }

   private static final class NoTimestampResolver extends Resolver {
      public void addImplicitResolver(Tag tag, Pattern regexp, String first, int limit) {
         if (tag != Tag.TIMESTAMP) {
            super.addImplicitResolver(tag, regexp, first, limit);
         }
      }
   }

   private class OriginTrackingConstructor extends SafeConstructor {
      OriginTrackingConstructor(LoaderOptions loadingConfig) {
         super(loadingConfig);
      }

      public @Nullable Object getData() throws NoSuchElementException {
         Object data = super.getData();
         return data instanceof CharSequence charSequence && charSequence.isEmpty() ? null : data;
      }

      protected Object constructObject(Node node) {
         if (node instanceof SequenceNode sequenceNode && sequenceNode.getValue().isEmpty()) {
            return this.constructTrackedObject(node, "");
         } else {
            if (node instanceof ScalarNode && !(node instanceof OriginTrackedYamlLoader.KeyScalarNode)) {
               return this.constructTrackedObject(node, super.constructObject(node));
            }

            if (node instanceof MappingNode mappingNode) {
               this.replaceMappingNodeKeys(mappingNode);
            }

            return super.constructObject(node);
         }
      }

      private void replaceMappingNodeKeys(MappingNode node) {
         List<NodeTuple> value = node.getValue();
         List<NodeTuple> newValue = new ArrayList<>(value.size());

         for (NodeTuple tuple : value) {
            newValue.add(OriginTrackedYamlLoader.KeyScalarNode.get(tuple));
         }

         node.setValue(newValue);
      }

      private Object constructTrackedObject(Node node, Object value) {
         Origin origin = this.getOrigin(node);
         return OriginTrackedValue.of(this.getValue(value), origin);
      }

      private Object getValue(@Nullable Object value) {
         return value != null ? value : "";
      }

      private Origin getOrigin(Node node) {
         Mark mark = node.getStartMark();
         TextResourceOrigin.Location location = new TextResourceOrigin.Location(mark.getLine(), mark.getColumn());
         return new TextResourceOrigin(OriginTrackedYamlLoader.this.resource, location);
      }
   }
}
