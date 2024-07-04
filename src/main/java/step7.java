import com.github.weisj.jsvg.S;
import com.google.gson.JsonArray;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.ui.Messages;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minidev.json.JSONArray;
import org.jetbrains.annotations.NotNull;

import java.io.FileReader;
import java.io.IOException;

public class step7 extends AnAction {

    //import a file that tells how to substitute one expression with another
    private static final String CONFIG_FILE_PATH = "D:/UROP/step7/src/main/resources/Test/data.json";
    private JsonObject configData;

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);

        if (psiFile instanceof PsiJavaFile javaFile) {
            WriteCommandAction.runWriteCommandAction(project, () -> {
                PsiClass[] classes = javaFile.getClasses();
                //System.out.println("psiFile: " + psiFile);
                // Load the configuration data from the JSON file
                loadConfigData();
                //System.out.println("Loaded configuration data: " + configData);
                for (PsiClass cls : classes) {
                    //System.out.println("Processing class: " + cls.getName());
                    replaceAPICallsInClass(cls);
                }
            });

            Messages.showInfoMessage("API call replacements complete!", "API Migration Tool");
        } else {
            Messages.showErrorDialog("Please select a Java file.", "API Migration Tool");
        }
    }

    private void replaceAPICallsInClass(PsiClass cls) {
        cls.accept(new JavaRecursiveElementVisitor() {
            @Override
            public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
                super.visitMethodCallExpression(expression);
                PsiReferenceExpression methodExpression = expression.getMethodExpression();
                String methodName = methodExpression.getReferenceName();
                if (methodName == null) {
                    return;
                }

                // Check if the method name matches any of the configured replacements
                JsonArray apis = configData.getAsJsonArray("apis");

                for (int i = 0; i < apis.size(); i++) {
                    if (apis.get(i) == null) {
                        continue;
                    }
                    JsonObject api = apis.get(i).getAsJsonObject();
                    String oldAPI = api.get("oldName").getAsString();
                    String newAPI = api.get("newName").getAsString();
                    JsonArray oldAPIParams = api.getAsJsonArray("oldApiParams");
                    JsonArray newAPIParams = api.getAsJsonArray("newApiParams");

                    if (oldAPI == null) {
                        continue;
                    }

                    if (methodName.equals(oldAPI)) {
                        // Check if the arguments match the old API parameters
                        PsiExpression[] arguments = expression.getArgumentList().getExpressions();
                        if (arguments.length == oldAPIParams.size()) {
                            boolean argsMatch = true;
                            for (int j = 0; j < arguments.length; j++) {
                                PsiExpression arg = arguments[j];
                                int oldParam = oldAPIParams.get(j).getAsInt();
                                if (!arg.getText().equals(String.valueOf(oldParam))) {
                                    argsMatch = false;
                                    break;
                                }
                            }

                            if (argsMatch) {
                                // Replace the method call with the new API
                                replaceWithNewAPI(expression, newAPI, newAPIParams);
                                break;
                            }
                        }
                    }
                }
            }
        });
    }

    private void replaceWithNewAPI(PsiMethodCallExpression expression, String newAPI, JsonArray newAPIParams) {
        PsiElementFactory factory = PsiElementFactory.getInstance(expression.getProject());
        StringBuilder newArguments = new StringBuilder();
        for (int i = 0; i < newAPIParams.size(); i++) {
            if (i > 0) {
                newArguments.append(", ");
            }
            newArguments.append(newAPIParams.get(i).getAsString());
        }
        PsiMethodCallExpression newExpression = (PsiMethodCallExpression) factory.createExpressionFromText(
                newAPI + "(" + newArguments + ")", expression);
        expression.replace(newExpression);
    }


    private void loadConfigData() {
        try (FileReader reader = new FileReader(CONFIG_FILE_PATH)) {
            Gson gson = new Gson();
            configData = gson.fromJson(reader, JsonObject.class);
        } catch (IOException e) {
            Messages.showErrorDialog("Error loading configuration file: " + e.getMessage(), "API Migration Tool");
        }
    }

}
