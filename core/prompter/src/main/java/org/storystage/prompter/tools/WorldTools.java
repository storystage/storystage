/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.tools;

import com.anthropic.core.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;
import org.alice.ide.IDE;
import org.alice.ide.ast.draganddrop.BlockStatementIndexPair;
import org.alice.ide.croquet.edits.ast.DeclareGalleryFieldEdit;
import org.alice.ide.croquet.edits.ast.InsertStatementEdit;
import org.alice.ide.instancefactory.ThisFieldAccessFactory;
import org.alice.ide.sceneeditor.AbstractSceneEditor;
import org.alice.math.immutable.AffineMatrix4x4;
import org.alice.math.immutable.AngleInDegrees;
import org.alice.math.immutable.OrthogonalMatrix3x3;
import org.alice.math.immutable.Point3;
import org.alice.math.immutable.Vector3;
import org.alice.stageide.StageIDE;
import org.alice.stageide.modelresource.EnumConstantResourceKey;
import org.alice.stageide.oneshot.edits.LocalTransformationEdit;
import org.alice.stageide.oneshot.edits.SetOpacityEdit;
import org.alice.stageide.oneshot.edits.SetPaintEdit;
import org.lgna.croquet.edits.Edit;
import org.lgna.croquet.history.UserActivity;
import org.lgna.project.annotations.ClassTemplate;
import org.lgna.project.ast.AbstractStatementWithBody;
import org.lgna.project.ast.AccessLevel;
import org.lgna.project.ast.AstUtilities;
import org.lgna.project.ast.BlockStatement;
import org.lgna.project.ast.ConditionalStatement;
import org.lgna.project.ast.DoInOrder;
import org.lgna.project.ast.DoTogether;
import org.lgna.project.ast.Expression;
import org.lgna.project.ast.ExpressionStatement;
import org.lgna.project.ast.FieldModifierFinalVolatileOrNeither;
import org.lgna.project.ast.InstanceCreation;
import org.lgna.project.ast.IntegerLiteral;
import org.lgna.project.ast.JavaKeyedArgument;
import org.lgna.project.ast.JavaMethod;
import org.lgna.project.ast.ManagementLevel;
import org.lgna.project.ast.MethodInvocation;
import org.lgna.project.ast.NamedUserType;
import org.lgna.project.ast.Node;
import org.lgna.project.ast.Statement;
import org.lgna.project.ast.UserField;
import org.lgna.project.ast.UserMethod;
import org.lgna.project.ast.UserType;
import org.lgna.story.Color;
import org.lgna.story.Paint;
import org.lgna.story.Position;
import org.lgna.story.SMovableTurnable;
import org.lgna.story.STurnable;
import org.lgna.story.TurnDirection;
import org.lgna.story.implementation.alice.AliceResourceUtilities;
import org.storystage.prompter.claude.Json;
import org.storystage.prompter.claude.ToolExecutor;
import org.storystage.prompter.context.ApiCatalog;
import org.storystage.prompter.context.NodeIds;
import org.storystage.prompter.context.ProjectText;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Runs the Prompter's tools against the open world. Every change is built with the same edit
 * classes the Alice UI uses and committed on the project activity, so it appears in Project
 * History and can be undone. All work runs on the Swing thread. See
 * docs/spec/01-prompter-protocol.md §3 and docs/spec/00-architecture.md §5.
 */
public final class WorldTools implements ToolExecutor {
  /** Tools that change nothing, allowed in explain-only mode. */
  public static final List<String> READ_ONLY_TOOLS = List.of(ToolSchemas.EXPLAIN, ToolSchemas.READ_PROJECT);

  private final NodeIds ids;
  private final GalleryCatalog gallery;
  private final boolean explainOnly;
  private volatile boolean requestExplainOnly;
  private final List<Edit> committed = new ArrayList<>();

  public WorldTools(NodeIds ids, GalleryCatalog gallery, boolean explainOnly) {
    this.ids = ids;
    this.gallery = gallery;
    this.explainOnly = explainOnly;
  }

  /** The edits made by the current request, newest last, for "Undo this request". */
  public synchronized List<Edit> getCommittedEdits() {
    return new ArrayList<>(this.committed);
  }

  /** Set for each request: true when the learner chose "Explain only" for it. */
  public void setRequestExplainOnly(boolean requestExplainOnly) {
    this.requestExplainOnly = requestExplainOnly;
  }

  public synchronized void clearCommittedEdits() {
    this.committed.clear();
  }

  @Override
  public Result execute(String toolName, JsonValue input) {
    JsonNode in = Json.tree(input);
    if (this.explainOnly && !READ_ONLY_TOOLS.contains(toolName)) {
      return Result.error("not_allowed_here", "The Prompter is set to explain only; it can't change the world.");
    }
    if (this.requestExplainOnly && !READ_ONLY_TOOLS.contains(toolName)) {
      return Result.error("not_allowed_here", "The learner chose Explain only for this request; explain instead of changing the world.");
    }
    AtomicReference<Result> result = new AtomicReference<>();
    Runnable work = () -> result.set(this.executeOnSwingThread(toolName, in));
    try {
      if (SwingUtilities.isEventDispatchThread()) {
        work.run();
      } else {
        SwingUtilities.invokeAndWait(work);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Result.error("would_break_world", "Interrupted.");
    } catch (InvocationTargetException e) {
      return Result.error("would_break_world", "StoryStage couldn't do that step (" + e.getCause().getClass().getSimpleName() + ").");
    }
    return result.get();
  }

  private Result executeOnSwingThread(String toolName, JsonNode in) {
    try {
      switch (toolName) {
      case ToolSchemas.EXPLAIN:
        return Result.ok(ok(Map.of()), text(in, "text"));
      case ToolSchemas.READ_PROJECT:
        return Result.ok(ok(Map.of("project", ProjectText.render(IDE.getActiveInstance().getProject(), this.ids))), null);
      case ToolSchemas.ADD_MODEL:
        return this.addModel(in);
      case ToolSchemas.SET_PROPERTY:
        return this.setProperty(in);
      case ToolSchemas.INSERT_STATEMENT:
        return this.insertStatement(in);
      case ToolSchemas.INSERT_CONTROL:
        return this.insertControl(in);
      default:
        return Result.error("unknown_method", "There's no tool called " + toolName + ".");
      }
    } catch (ValueConverter.BadValue e) {
      return Result.error("bad_argument", e.getMessage());
    } catch (ToolError e) {
      return Result.error(e.code, e.getMessage());
    }
  }

  // ---- add_model -------------------------------------------------------------------------

  private Result addModel(JsonNode in) throws ToolError, ValueConverter.BadValue {
    String itemId = text(in, "gallery_item");
    EnumConstantResourceKey key = this.gallery.get(itemId);
    if (key == null) {
      throw new ToolError("bad_argument", "There's no gallery model " + itemId + ". Use an id from the gallery list.");
    }
    StageIDE ide = StageIDE.getActiveInstance();
    NamedUserType sceneType = ide.getSceneType();
    AbstractSceneEditor sceneEditor = ide.getSceneEditor();
    InstanceCreation initializer = key.createInstanceCreation();
    UserField field = new UserField();
    field.finalVolatileOrNeither.setValue(FieldModifierFinalVolatileOrNeither.FINAL);
    field.accessLevel.setValue(AccessLevel.PRIVATE);
    field.managementLevel.setValue(ManagementLevel.MANAGED);
    field.valueType.setValue(initializer.constructor.getValue().getDeclaringType());
    field.name.setValue(this.uniqueName(sceneType, identifier(text(in, "name"))));
    field.initializer.setValue(initializer);

    AffineMatrix4x4 base = AliceResourceUtilities.getDefaultInitialTransform(key.getModelResourceCls());
    OrthogonalMatrix3x3 orientation = base != null ? base.orientation() : OrthogonalMatrix3x3.IDENTITY;
    double turn = number(in, "turn_degrees", 0);
    if (turn != 0) {
      orientation = orientation.applyRotationAboutArbitraryAxis(new Vector3(0, 1, 0), new AngleInDegrees(turn));
    }
    JsonNode p = in.path("position");
    Point3 position = new Point3(p.path("x").asDouble(0), p.path("y").asDouble(0), p.path("z").asDouble(0));
    AffineMatrix4x4 transform = new AffineMatrix4x4(orientation, position);

    Statement[] doStatements = sceneEditor.getDoStatementsForAddField(field, transform);
    Statement[] undoStatements = sceneEditor.getUndoStatementsForAddField(field);
    this.commit(activity -> new DeclareGalleryFieldEdit(activity, sceneEditor, sceneType, field, doStatements, undoStatements));
    return Result.ok(ok(Map.of("ids", Map.of("object", field.getName()))), text(in, "narration"));
  }

  private String uniqueName(UserType<?> sceneType, String wanted) {
    String base = wanted.isEmpty() ? "thing" : wanted;
    String candidate = base;
    int n = 2;
    while (true) {
      boolean taken = false;
      for (UserField f : sceneType.fields) {
        if (f.getName().equals(candidate)) {
          taken = true;
          break;
        }
      }
      if (!taken) {
        return candidate;
      }
      candidate = base + n++;
    }
  }

  // ---- set_property ----------------------------------------------------------------------

  private Result setProperty(JsonNode in) throws ToolError, ValueConverter.BadValue {
    StageIDE ide = StageIDE.getActiveInstance();
    ValueConverter values = new ValueConverter(ide.getSceneType());
    String objectName = text(in, "object");
    UserField field = values.field(objectName);
    if (field == null) {
      throw new ToolError("unknown_object", "There's no object called " + objectName + " in the scene.");
    }
    String property = text(in, "property");
    String value = text(in, "value");
    ThisFieldAccessFactory target = ThisFieldAccessFactory.getInstance(field);
    switch (property) {
    case "position":
      requireType(field, SMovableTurnable.class, objectName);
      this.setPosition(target, numbers(value, 3));
      break;
    case "turn_degrees":
      requireType(field, STurnable.class, objectName);
      this.setTurn(target, ValueConverter.parseNumber(value));
      break;
    case "paint":
      this.setPaint(target, ValueConverter.constant(Color.class, value));
      break;
    case "opacity":
      this.setOpacity(target, Math.max(0, Math.min(1, ValueConverter.parseNumber(value))));
      break;
    default:
      throw new ToolError("bad_argument", "Properties you can set are position, turn_degrees, paint and opacity.");
    }
    return Result.ok(ok(Map.of()), text(in, "narration"));
  }

  private void setPosition(ThisFieldAccessFactory target, double[] xyz) throws ToolError {
    JavaMethod setter = JavaMethod.getInstance(SMovableTurnable.class, "setPositionRelativeToVehicle", Position.class, org.lgna.story.SetPositionRelativeToVehicle.Detail[].class);
    Expression[] args = {this.create(new Position(xyz[0], xyz[1], xyz[2]))};
    this.commit(activity -> new LocalTransformationEdit(activity, target, setter, args));
  }

  private void setTurn(ThisFieldAccessFactory target, double degrees) throws ToolError {
    JavaMethod turn = JavaMethod.getInstance(STurnable.class, "turn", TurnDirection.class, Number.class, org.lgna.story.Turn.Detail[].class);
    Expression[] args = {this.create(degrees >= 0 ? TurnDirection.LEFT : TurnDirection.RIGHT), new org.lgna.project.ast.DoubleLiteral(Math.abs(degrees) / 360.0)};
    this.commit(activity -> new LocalTransformationEdit(activity, target, turn, args));
  }

  private void setPaint(ThisFieldAccessFactory target, Object paint) throws ToolError {
    JavaMethod setter = JavaMethod.getInstance(org.lgna.story.SModel.class, "setPaint", Paint.class, org.lgna.story.SetPaint.Detail[].class);
    Expression[] args = {this.create(paint)};
    this.commit(activity -> new SetPaintEdit(activity, target, setter, args));
  }

  private void setOpacity(ThisFieldAccessFactory target, double opacity) {
    JavaMethod setter = JavaMethod.getInstance(org.lgna.story.SModel.class, "setOpacity", Number.class, org.lgna.story.SetOpacity.Detail[].class);
    Expression[] args = {new org.lgna.project.ast.DoubleLiteral(opacity)};
    this.commit(activity -> new SetOpacityEdit(activity, target, setter, args));
  }

  private static void requireType(UserField field, Class<?> cls, String name) throws ToolError {
    if (!field.getValueType().isAssignableTo(cls)) {
      throw new ToolError("not_allowed_here", name + " can't do that.");
    }
  }

  private Expression create(Object value) throws ToolError {
    try {
      return ValueConverter.expressionCreator().createExpression(value);
    } catch (org.alice.ide.ast.ExpressionCreator.CannotCreateExpressionException e) {
      throw new ToolError("bad_argument", "StoryStage can't use that value.");
    }
  }

  // ---- insert_statement ------------------------------------------------------------------

  private Result insertStatement(JsonNode in) throws ToolError, ValueConverter.BadValue {
    StageIDE ide = StageIDE.getActiveInstance();
    ValueConverter values = new ValueConverter(ide.getSceneType());
    BlockStatement block = this.findBlock(in);
    int index = in.path("position").asInt(-1);

    String targetName = text(in, "target");
    Expression instance = values.objectExpression(targetName, null);
    Class<?> targetCls = this.javaClassOf(instance, values, targetName);
    String procedure = text(in, "procedure");
    Map<String, String> arguments = pairs(in.path("arguments"));
    Map<String, String> details = pairs(in.path("details"));

    Method method = findProcedure(targetCls, procedure, arguments.size());
    if (method == null) {
      throw new ToolError("unknown_method", targetName + " has no block " + procedure + " with " + arguments.size() + " inputs. Check the catalog.");
    }
    ApiCatalog.Entry entry = entryFor(method);
    List<Expression> args = new ArrayList<>();
    for (int i = 0; i < entry.parameterTypes.size(); i++) {
      String name = entry.parameterNames.get(i);
      String raw = arguments.containsKey(name) ? arguments.get(name) : nthValue(arguments, i);
      if (raw == null) {
        throw new ToolError("bad_argument", procedure + " needs " + name + ".");
      }
      args.add(values.convert(raw, entry.parameterTypes.get(i)));
    }
    JavaMethod javaMethod = JavaMethod.getInstance(method);
    MethodInvocation invocation = AstUtilities.createMethodInvocation(instance, javaMethod, args.toArray(new Expression[0]));
    for (Map.Entry<String, String> d : details.entrySet()) {
      Class<?> detailType = entry.details.get(d.getKey());
      if (detailType == null) {
        throw new ToolError("bad_argument", procedure + " has no detail " + d.getKey() + ". Details it takes: " + entry.details.keySet() + ".");
      }
      Method factory = keywordFactory(method, d.getKey(), detailType);
      invocation.keyedArguments.add(new JavaKeyedArgument(invocation.method.getValue().getKeyedParameter(), JavaMethod.getInstance(factory), values.convert(d.getValue(), detailType)));
    }
    ExpressionStatement statement = new ExpressionStatement(invocation);
    int at = index < 0 ? InsertStatementEdit.AT_END : index;
    this.commit(activity -> new InsertStatementEdit<>(activity, new BlockStatementIndexPair(block, at), statement));
    return Result.ok(ok(Map.of("ids", Map.of("block", this.ids.idOf(statement)))), text(in, "narration"));
  }

  private Class<?> javaClassOf(Expression instance, ValueConverter values, String targetName) {
    UserField field = values.field(targetName);
    if (field != null) {
      return field.getValueType().getFirstEncounteredJavaType().getClassReflectionProxy().getReification();
    }
    return StageIDE.getActiveInstance().getSceneType().getFirstEncounteredJavaType().getClassReflectionProxy().getReification();
  }

  static Method findProcedure(Class<?> cls, String name, int argumentCount) {
    Method best = null;
    for (Method m : cls.getMethods()) {
      if (m.getName().equals(name) && isPrimeTimeAnywhere(m)) {
        int required = m.isVarArgs() ? m.getParameterCount() - 1 : m.getParameterCount();
        if (required == argumentCount) {
          return m;
        }
        if (best == null) {
          best = m;
        }
      }
    }
    return argumentCount == 0 ? null : best;
  }

  private static boolean isPrimeTimeAnywhere(Method m) {
    try {
      return ApiCatalog.isPrimeTime(m.getDeclaringClass().getDeclaredMethod(m.getName(), m.getParameterTypes()));
    } catch (NoSuchMethodException e) {
      return false;
    }
  }

  private static ApiCatalog.Entry entryFor(Method m) {
    for (ApiCatalog.Entry e : ApiCatalog.entries()) {
      if (e.declaringClass == m.getDeclaringClass() && e.name.equals(m.getName()) && e.parameterTypes.size() == (m.isVarArgs() ? m.getParameterCount() - 1 : m.getParameterCount())) {
        return e;
      }
    }
    return ApiCatalog.entryOf(m);
  }

  private static Method keywordFactory(Method method, String detailName, Class<?> detailType) throws ToolError {
    Class<?> detailInterface = method.getParameterTypes()[method.getParameterCount() - 1].getComponentType();
    ClassTemplate template = detailInterface.getAnnotation(ClassTemplate.class);
    try {
      return template.keywordFactoryCls().getMethod(detailName, detailType);
    } catch (NoSuchMethodException | NullPointerException e) {
      throw new ToolError("bad_argument", "The detail " + detailName + " can't be used here.");
    }
  }

  // ---- insert_control --------------------------------------------------------------------

  private Result insertControl(JsonNode in) throws ToolError {
    BlockStatement block = this.findBlock(in);
    int index = in.path("position").asInt(-1);
    String kind = text(in, "kind");
    Statement statement;
    switch (kind) {
    case "do_in_order":
      statement = new DoInOrder(new BlockStatement());
      break;
    case "do_together":
      statement = new DoTogether(new BlockStatement());
      break;
    case "count":
      int count = Math.max(1, Math.min(1000, in.path("count").asInt(2)));
      statement = AstUtilities.createCountLoop(new IntegerLiteral(count));
      break;
    default:
      throw new ToolError("not_allowed_here", "In this version the Prompter can add do_in_order, do_together and count blocks.");
    }
    int at = index < 0 ? InsertStatementEdit.AT_END : index;
    Statement toInsert = statement;
    this.commit(activity -> new InsertStatementEdit<>(activity, new BlockStatementIndexPair(block, at), toInsert));
    return Result.ok(ok(Map.of("ids", Map.of("block", this.ids.idOf(statement)))), text(in, "narration"));
  }

  // ---- shared ----------------------------------------------------------------------------

  /** The block to insert into: a control block by id, or a method's body. */
  private BlockStatement findBlock(JsonNode in) throws ToolError {
    String parentId = text(in, "parent_block");
    if (!parentId.isEmpty()) {
      Node node = this.ids.lookup(parentId);
      if (node instanceof AbstractStatementWithBody withBody) {
        return withBody.body.getValue();
      }
      if (node instanceof ConditionalStatement conditional) {
        return conditional.booleanExpressionBodyPairs.get(0).body.getValue();
      }
      throw new ToolError("unknown_block", "There's no control block " + parentId + ". Use an id from the project text or a previous result.");
    }
    String methodName = text(in, "method");
    String typeName = methodName.contains(".") ? methodName.substring(0, methodName.indexOf('.')) : "Scene";
    String name = methodName.contains(".") ? methodName.substring(methodName.indexOf('.') + 1) : methodName;
    for (NamedUserType type : IDE.getActiveInstance().getProject().getNamedUserTypes()) {
      if (type.getName().equals(typeName)) {
        for (UserMethod m : type.methods) {
          if (m.getName().equals(name)) {
            return m.body.getValue();
          }
        }
      }
    }
    throw new ToolError("unknown_method", "There's no method " + methodName + ". Use Scene.myFirstMethod or another method in the project.");
  }

  private void commit(Function<UserActivity, Edit> editFactory) {
    UserActivity activity = IDE.getActiveInstance().getProjectUserActivity().newChildActivity();
    activity.setCompletionModel(PrompterStepOperation.getInstance());
    Edit edit = editFactory.apply(activity);
    activity.commitAndInvokeDo(edit);
    synchronized (this) {
      this.committed.add(edit);
    }
  }

  private static String text(JsonNode in, String field) {
    JsonNode n = in.path(field);
    return n.isMissingNode() || n.isNull() ? "" : n.asText("").trim();
  }

  private static double number(JsonNode in, String field, double defaultValue) {
    JsonNode n = in.path(field);
    return n.isNumber() ? n.asDouble() : defaultValue;
  }

  private static double[] numbers(String value, int count) throws ValueConverter.BadValue {
    String[] parts = value.replace("(", "").replace(")", "").split("[,\\s]+");
    if (parts.length != count) {
      throw new ValueConverter.BadValue("Expected " + count + " numbers like \"0, 0, 2\", got \"" + value + "\".");
    }
    double[] rv = new double[count];
    for (int i = 0; i < count; i++) {
      rv[i] = ValueConverter.parseNumber(parts[i]);
    }
    return rv;
  }

  /** A list of {"name": ..., "value": ...} pairs as a map, in order. */
  private static Map<String, String> pairs(JsonNode list) {
    Map<String, String> rv = new LinkedHashMap<>();
    if (list.isArray()) {
      for (JsonNode pair : list) {
        rv.put(pair.path("name").asText(""), pair.path("value").asText(""));
      }
    }
    return rv;
  }

  private static String nthValue(Map<String, String> map, int n) {
    int i = 0;
    for (String v : map.values()) {
      if (i++ == n) {
        return v;
      }
    }
    return null;
  }

  private static String identifier(String name) {
    StringBuilder sb = new StringBuilder();
    for (char c : name.toCharArray()) {
      if (Character.isLetterOrDigit(c) || c == '_') {
        sb.append(sb.length() == 0 ? Character.toLowerCase(c) : c);
      }
    }
    if (sb.length() > 0 && Character.isDigit(sb.charAt(0))) {
      sb.insert(0, '_');
    }
    return sb.toString();
  }

  private static String ok(Map<String, ?> extra) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("ok", true);
    map.putAll(extra);
    return Json.write(map);
  }

  /** A failure with an error code from spec 01 §3. */
  static final class ToolError extends Exception {
    final String code;

    ToolError(String code, String message) {
      super(message);
      this.code = code;
    }
  }
}
