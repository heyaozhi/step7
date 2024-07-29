
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
    private static final String CONFIG_FILE_PATH = "C:/Users/ZHAO_Yuhua/Desktop/step7/src/main/resources/Test/data.json";
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
                System.out.println("heyaozhi ");
                //System.out.println("Loaded configuration data: " + configData);
                for (PsiClass cls : classes) {
                    System.out.println("Processing class: " + cls.getName());
                    replaceAPICallsInClass(cls,psiFile);
                }
            });

            System.out.println("Checking message ");
            Messages.showInfoMessage("API call replacements complete!", "API Migration Tool");
        } else {
            Messages.showErrorDialog("Please select a Java file.", "API Migration Tool");
        }
    }
    private void rgb2hsl(double r, double g, double b, double a, double[] newAPIParams) {
        r /= 255.0f;
        g /= 255.0f;
        b /= 255.0f;
        a /= 255.0f;
        double c_max = Math.max(r, Math.max(g, b));
        double c_min = Math.min(r, Math.min(g, b));
        double c = c_max - c_min;
        double h, s, l, v;
        if (c == 0) {
            h = 0;
        } else if (c_max == r) {
            h = (g - b) / c + (g < b ? 6 : 0);
        } else if (c_max == g) {
            h = (b - r) / c + 2;
        } else { // c_max == b
            h = (r - g) / c + 4;
        }
        h = Math.round(h * 60);
        l = (c_max + c_min) / 2.0f;
        v = Math.min(l, 1 - l) * 2.0f;
        s = v == 0 ? 0 : c / v;
        newAPIParams[0]=h;
        newAPIParams[1]=s;
        newAPIParams[2]=l;
        newAPIParams[3]=a;
    }

    private void replaceAPICallsInClass(PsiClass cls, PsiFile psiFile) {
        cls.accept(new JavaRecursiveElementVisitor() {
            @Override
            public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
                super.visitMethodCallExpression(expression);
                //get the name of the expression
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
                        //make sure two references
                        PsiExpression arg1 = arguments[0];
                        PsiExpression arg2 = arguments[1];
                        String replacedReturn = "assertTrue(" + arg1.getText() + ".equals(" + arg2.getText() + "))";
                        // replace the expression with the replaced return
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
                        // get the old name and the new name from the json expression
                        JsonObject api = apis.get(i).getAsJsonObject();
                        String oldAPI = api.get("oldName").getAsString();
                        String newAPI = api.get("newName").getAsString();
                        if (oldAPI == null) {
                            continue;
                        }
                        if (methodName.equals(oldAPI)&&(methodName.equals("color1"))) {
                            // why to check the line number?

                            int lineNumber = api.get("lineNumber").getAsInt();
                            if (getLineNumber(expression, psiFile) == lineNumber) {
                                PsiExpression[] arguments = expression.getArgumentList().getExpressions();

                                double[] newAPIParams= new double[4];
                                double r,g,b,a;
                                r=Double.parseDouble(arguments[0].getText());
                                g=Double.parseDouble(arguments[1].getText());
                                b=Double.parseDouble(arguments[2].getText());
                                a=Double.parseDouble(arguments[3].getText());
                                rgb2hsl(r,g,b,a,newAPIParams);



                                //JsonArray oldAPIParams = api.getAsJsonArray("oldApiParams");

                                //JsonArray newAPIParams = api.getAsJsonArray("newApiParams");

                                JsonArray oldPreParams = api.getAsJsonArray("oldPreParams");
                                String oldReturn = api.get("oldReturn").getAsString();

                                JsonArray newPreParams = api.getAsJsonArray("newPreParams");
                                String newReturn = api.get("newReturn").getAsString();

                                // Check if the arguments match the old API parameters
                                PsiElement parentExpression = expression.getParent();
                                replaceWithNewAPI(expression, newAPI, newAPIParams, newReturn, newPreParams,parentExpression);



                                /*if (arguments.length == oldAPIParams.size()) {
                                /*Document document = psiFile.getViewProvider().getDocument();
                                int line = 4;
                                int lineStartOffset = document.getLineStartOffset(line);
                                int lineEndOffset = document.getLineEndOffset(line);
                                PsiElement lineElement = psiFile.findElementAt(lineStartOffset);*/
                                    /*boolean argsMatch = true;
                                    for (int j = 0; j < arguments.length; j++) {
                                        PsiExpression arg = arguments[j];
                                        int oldParam = oldAPIParams.get(j).getAsInt();
                                        //if it doesn't match return false
                                        // but what is the need for us to match
                                        if (!arg.getText().equals(String.valueOf(oldParam))) {
                                            argsMatch = false;
                                            break;
                                        }
                                    }

                                    if (argsMatch) {
                                        // Replace the method call with the new API

                                        break;
                                    }*/

                            }
                        }
                    }
                }
            }
        });
    }

    private void replaceWithNewAPI(PsiMethodCallExpression expression, String newAPI, double[] newAPIParams, String newReturn, JsonArray newPreParams,PsiElement parentElement) {
        PsiElementFactory factory = PsiElementFactory.getInstance(expression.getProject());
//what is the meaning of this line
        StringBuilder newArguments = new StringBuilder();
        for (int i = 0; i < newAPIParams.length; i++) {
            if (i > 0) {
                newArguments.append(", ");
            }
            newArguments.append(String.valueOf(newAPIParams[i]));
        }
        System.out.println("expression: " + expression);
        String replacedReturn = newReturn.replace(newAPI, newAPI + "(" + newArguments + ")");
        System.out.println("replacedReturn: " + replacedReturn);
        PsiExpression newExpression = (PsiExpression) factory.createExpressionFromText(
                replacedReturn, expression);
        //System.out.println("newExpression: " + newExpression);
        expression.replace(newExpression);
        System.out.println("parentExpression: " + parentElement);
        if(parentElement instanceof PsiExpression){
            simplifyExpression(parentElement);
        }
    }

// can't understand the parent thing
    private void simplifyExpression(PsiElement parentElement) {
        //use Smyja to simplify the expression
        if (parentElement instanceof PsiExpression parent) {
            PsiElementFactory factory = PsiElementFactory.getInstance(parentElement.getProject());
            String parentText = parent.getText();

        }
    }

    private void loadConfigData() {
        //load things from json objects
        //try is if nothing wrong happened when running try, then skip catch, else skip try
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
