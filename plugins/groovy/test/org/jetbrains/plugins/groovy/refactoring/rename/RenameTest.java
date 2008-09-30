package org.jetbrains.plugins.groovy.refactoring.rename;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.*;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.util.IncorrectOperationException;
import junit.framework.Assert;
import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.refactoring.CommonRefactoringTestCase;
import org.jetbrains.plugins.groovy.util.PathUtil;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * @author ven
 */
public class RenameTest extends CommonRefactoringTestCase {
  private static final String DATA_PATH = PathUtil.getDataPath(RenameTest.class);

  public RenameTest() {
    super(System.getProperty("path") != null ? System.getProperty("path") : DATA_PATH);
  }

  protected String processFile(String fileText) throws IncorrectOperationException, InvalidDataException, IOException {
    String result;
    int caretOffset = fileText.indexOf(CARET_MARKER);
    fileText = TestUtils.removeCaretMarker(fileText);
    PsiFile file = TestUtils.createPseudoPhysicalGroovyFile(myProject, fileText);

    Assert.assertNotNull(file);

    // Create physical file
    String modulePath = getMyFixture().getTempDirPath();
    File realFile = new File(modulePath + "/" + TestUtils.TEMP_FILE);
    FileWriter fstream = new FileWriter(realFile);
    BufferedWriter out = new BufferedWriter(fstream);
    out.write(file.getText());
    out.close();

    VirtualFileManager fileManager = VirtualFileManager.getInstance();
    fileManager.refresh(false);
    VirtualFile virtualFile = fileManager.findFileByUrl("file://" + modulePath + "/" + TestUtils.TEMP_FILE);

    assert virtualFile != null;

    FileEditorManager fileEditorManager = FileEditorManager.getInstance(myProject);
    Editor myEditor = fileEditorManager.openTextEditor(new OpenFileDescriptor(myProject, virtualFile, 0), false);
    assert myEditor != null;


    file = PsiManager.getInstance(myProject).findFile(virtualFile);
    Assert.assertTrue(file instanceof GroovyFile);

    try {
      myEditor.getCaretModel().moveToOffset(caretOffset);

      PsiReference ref = file.findReferenceAt(caretOffset);
      assert ref != null;


      final PsiElement resolved = ref.resolve();
      assert resolved != null;
      CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              if (resolved instanceof PsiMethod && !(resolved instanceof GrAccessorMethod)) {
                PsiMethod method = (PsiMethod)resolved;
                String name = method.getName();
                String newName = createNewNameForMethod(name);
                new RenameProcessor(myProject, resolved, newName, true, true).run();
              } else if (resolved instanceof GrAccessorMethod) {
                GrField field = ((GrAccessorMethod)resolved).getProperty();
                RenameProcessor processor = new RenameProcessor(myProject, field, "newName", true, true);
                processor.addElement(resolved, createNewNameForMethod(((GrAccessorMethod)resolved).getName()));
                processor.run();
              } else {
                new RenameProcessor(myProject, resolved, "newName", true, true).run();
              }
            }
          });
        }
      }, "Rename", null);

      result = myEditor.getDocument().getText();
    }
    finally {
      fileEditorManager.closeFile(virtualFile);
      realFile.delete();
      myEditor = null;
    }

    //System.out.println(result);

    return result;

  }

  private String createNewNameForMethod(final String name) {
    String newName = "newName";
    if (name.startsWith("get")) {
      newName = "get" + StringUtil.capitalize(newName);
    } else if (name.startsWith("is")) {
      newName = "is" + StringUtil.capitalize(newName);
    } else if (name.startsWith("set")) {
      newName = "set" + StringUtil.capitalize(newName);
    }
    return newName;
  }

  public static Test suite() {
    return new RenameTest();
  }

  private static class MyDataContext implements DataContext, DataProvider {

    PsiFile myFile;

    public MyDataContext(PsiFile file) {
      myFile = file;
    }

    @Nullable
    public Object getData(@NonNls String dataId) {
      if (DataConstants.LANGUAGE.equals(dataId)) return myFile.getLanguage();
      if (DataConstants.PROJECT.equals(dataId)) return myFile.getProject();
      if (DataConstants.PSI_FILE.equals(dataId)) return myFile;
      return null;
    }
  }
}
