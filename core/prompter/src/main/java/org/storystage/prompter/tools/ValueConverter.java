/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.tools;

import org.alice.ide.ast.ExpressionCreator;
import org.alice.stageide.StageIDE;
import org.lgna.project.ast.BooleanLiteral;
import org.lgna.project.ast.DoubleLiteral;
import org.lgna.project.ast.Expression;
import org.lgna.project.ast.FieldAccess;
import org.lgna.project.ast.IntegerLiteral;
import org.lgna.project.ast.StringLiteral;
import org.lgna.project.ast.ThisExpression;
import org.lgna.project.ast.UserField;
import org.lgna.project.ast.UserType;
import org.lgna.story.SThing;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Locale;

/**
 * Turns Claude's text values into Alice expressions for a parameter type: numbers, text, true or
 * false, named constants (MoveDirection.FORWARD, Color.RED) and objects in the scene.
 */
final class ValueConverter {
  /** Thrown with a message Claude can act on. */
  static final class BadValue extends Exception {
    BadValue(String message) {
      super(message);
    }
  }

  private final UserType<?> sceneType;

  ValueConverter(UserType<?> sceneType) {
    this.sceneType = sceneType;
  }

  Expression convert(String raw, Class<?> type) throws BadValue {
    String value = raw == null ? "" : raw.trim();
    if (type == Double.class || type == Double.TYPE || type == Number.class || type == Float.class || type == Float.TYPE) {
      return new DoubleLiteral(parseNumber(value));
    }
    if (type == Integer.class || type == Integer.TYPE) {
      return new IntegerLiteral((int) Math.round(parseNumber(value)));
    }
    if (type == String.class) {
      return new StringLiteral(value);
    }
    if (type == Boolean.class || type == Boolean.TYPE) {
      if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
        return new BooleanLiteral(Boolean.parseBoolean(value));
      }
      throw new BadValue("Expected true or false, got \"" + value + "\".");
    }
    if (SThing.class.isAssignableFrom(type)) {
      return this.objectExpression(value, type);
    }
    Object constant = constant(type, value);
    try {
      return expressionCreator().createExpression(constant);
    } catch (ExpressionCreator.CannotCreateExpressionException e) {
      throw new BadValue("StoryStage can't use the value " + value + " here.");
    }
  }

  /** The expression for an object in the scene: "this" or "scene" for the scene itself. */
  Expression objectExpression(String name, Class<?> type) throws BadValue {
    String n = stripThis(name);
    if (n.isEmpty() || "this".equals(n) || "scene".equalsIgnoreCase(n)) {
      return new ThisExpression();
    }
    UserField field = this.field(n);
    if (field == null) {
      throw new BadValue("There's no object called " + n + " in the scene.");
    }
    if (type != null && !field.getValueType().isAssignableTo(type)) {
      throw new BadValue(n + " can't be used here: a " + type.getSimpleName() + " is needed.");
    }
    return new FieldAccess(field);
  }

  UserField field(String name) {
    String n = stripThis(name);
    for (UserField f : this.sceneType.fields) {
      if (f.getName().equals(n)) {
        return f;
      }
    }
    return null;
  }

  static String stripThis(String name) {
    String n = name == null ? "" : name.trim();
    return n.startsWith("this.") ? n.substring(5) : n;
  }

  static double parseNumber(String value) throws BadValue {
    try {
      double d = Double.parseDouble(value);
      if (Double.isNaN(d) || Double.isInfinite(d)) {
        throw new NumberFormatException();
      }
      return d;
    } catch (NumberFormatException e) {
      throw new BadValue("Expected a number, got \"" + value + "\".");
    }
  }

  /** An enum constant or public static constant of the type, by name (case-insensitive, prefix allowed). */
  static Object constant(Class<?> type, String value) throws BadValue {
    String name = value.contains(".") ? value.substring(value.lastIndexOf('.') + 1) : value;
    String upper = name.toUpperCase(Locale.ROOT).replace(' ', '_');
    if (type.isEnum()) {
      for (Object c : type.getEnumConstants()) {
        if (((Enum<?>) c).name().equals(upper)) {
          return c;
        }
      }
    } else {
      for (Field f : type.getFields()) {
        int mod = f.getModifiers();
        if (Modifier.isStatic(mod) && Modifier.isFinal(mod) && type.isAssignableFrom(f.getType()) && f.getName().equals(upper)) {
          try {
            return f.get(null);
          } catch (IllegalAccessException e) {
            break;
          }
        }
      }
    }
    throw new BadValue(value + " isn't a " + type.getSimpleName() + ". Use one of the values listed in the catalog.");
  }

  static ExpressionCreator expressionCreator() {
    return StageIDE.getActiveInstance().getApiConfigurationManager().getExpressionCreator();
  }
}
