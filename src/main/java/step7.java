
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
                    replaceAPICallsInClass(cls,psiFile);
                }
            });

            Messages.showInfoMessage("API call replacements complete!", "API Migration Tool");
        } else {
            Messages.showErrorDialog("Please select a Java file.", "API Migration Tool");
        }
    }

    private void replaceAPICallsInClass(PsiClass cls, PsiFile psiFile) {
        cls.accept(new JavaRecursiveElementVisitor() {
            @Override
            public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
                super.visitMethodCallExpression(expression);
                PsiReferenceExpression methodExpression = expression.getMethodExpression();
                String methodName = methodExpression.getReferenceName();
                if (methodName == null) {
                    return;
                }
                if(methodName.equals("assertEquals")){
                   //change assertEquals(a,b) to assertTrue(a.equals(b))
                    PsiElementFactory factory = PsiElementFactory.getInstance(expression.getProject());
                    PsiExpression[] arguments = expression.getArgumentList().getExpressions();
                    if (arguments.length == 2) {
                        PsiExpression arg1 = arguments[0];
                        PsiExpression arg2 = arguments[1];
                        String replacedReturn = "assertTrue(" + arg1.getText() + ".equals(" + arg2.getText() + "))";
                        PsiExpression newExpression = (PsiExpression) factory.createExpressionFromText(
                                replacedReturn, expression);
                        expression.replace(newExpression);
                    }
                }
                else {
                    // Check if the method name matches any of the configured replacements
                    JsonArray apis = configData.getAsJsonArray("apis");

                    for (int i = 0; i < apis.size(); i++) {
                        if (apis.get(i) == null) {
                            continue;
                        }
                        JsonObject api = apis.get(i).getAsJsonObject();
                        String oldAPI = api.get("oldName").getAsString();
                        String newAPI = api.get("newName").getAsString();
                        if (oldAPI == null) {
                            continue;
                        }
                        if (methodName.equals(oldAPI)) {
                            int lineNumber = api.get("lineNumber").getAsInt();
                            if (getLineNumber(expression, psiFile) == lineNumber) {
                                JsonArray oldAPIParams = api.getAsJsonArray("oldApiParams");
                                JsonArray newAPIParams = api.getAsJsonArray("newApiParams");

                                JsonArray oldPreParams = api.getAsJsonArray("oldPreParams");
                                String oldReturn = api.get("oldReturn").getAsString();

                                JsonArray newPreParams = api.getAsJsonArray("newPreParams");
                                String newReturn = api.get("newReturn").getAsString();

                                // Check if the arguments match the old API parameters
                                PsiExpression[] arguments = expression.getArgumentList().getExpressions();

                                if (arguments.length == oldAPIParams.size()) {
                                /*Document document = psiFile.getViewProvider().getDocument();
                                int line = 4;
                                int lineStartOffset = document.getLineStartOffset(line);
                                int lineEndOffset = document.getLineEndOffset(line);
                                PsiElement lineElement = psiFile.findElementAt(lineStartOffset);*/
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
                                        System.out.println("expression: " + expression);
                                        replaceWithNewAPI(expression, newAPI, newAPIParams, newReturn, newPreParams);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        });
    }

        private void replaceWithNewAPI(PsiMethodCallExpression expression, String newAPI, JsonArray newAPIParams, String newReturn, JsonArray newPreParams) {
        PsiElementFactory factory = PsiElementFactory.getInstance(expression.getProject());

        StringBuilder newArguments = new StringBuilder();
        for (int i = 0; i < newAPIParams.size(); i++) {
            if (i > 0) {
                newArguments.append(", ");
            }
            newArguments.append(newAPIParams.get(i).getAsString());
        }


        System.out.println("expression: " + expression);
        String replacedReturn = newReturn.replace(newAPI, newAPI + "(" + newArguments + ")");
        System.out.println("replacedReturn: " + replacedReturn);
        PsiExpression newExpression = (PsiExpression) factory.createExpressionFromText(
                replacedReturn, expression);
        System.out.println("newExpression: " + newExpression);

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

    private int getLineNumber(PsiElement element, PsiFile psiFile) {
        int offset = element.getTextOffset();
        return psiFile.getViewProvider().getDocument().getLineNumber(offset) + 1;
    }
}
