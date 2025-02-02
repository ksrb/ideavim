/*
 * IdeaVim - Vim emulator for IDEs based on the IntelliJ platform
 * Copyright (C) 2003-2021 The IdeaVim authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.maddyhome.idea.vim.group

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.maddyhome.idea.vim.VimPlugin
import com.maddyhome.idea.vim.command.CommandState
import com.maddyhome.idea.vim.command.SelectionType
import com.maddyhome.idea.vim.common.IndentConfig
import com.maddyhome.idea.vim.common.TextRange
import com.maddyhome.idea.vim.common.editor.EditorLine
import com.maddyhome.idea.vim.common.editor.IjVimCaret
import com.maddyhome.idea.vim.common.editor.IjVimEditor
import com.maddyhome.idea.vim.common.editor.OperatedRange
import com.maddyhome.idea.vim.common.editor.VimMachine
import com.maddyhome.idea.vim.common.editor.excl
import com.maddyhome.idea.vim.common.editor.indentForLine
import com.maddyhome.idea.vim.common.editor.offsetForLineWithStartOfLineOption
import com.maddyhome.idea.vim.common.editor.toVimRange
import com.maddyhome.idea.vim.helper.EditorHelper
import com.maddyhome.idea.vim.helper.inlayAwareVisualColumn
import com.maddyhome.idea.vim.helper.vimChangeActionSwitchMode
import com.maddyhome.idea.vim.helper.vimLastColumn

fun changeRange(
  editor: Editor,
  caret: Caret,
  range: TextRange,
  type: SelectionType,
  context: DataContext,
) {
  val vimEditor = IjVimEditor(editor)
  val vimRange = toVimRange(range, type)

  var col = 0
  var lines = 0
  if (type === SelectionType.BLOCK_WISE) {
    lines = ChangeGroup.getLinesCountInVisualBlock(editor, range)
    col = editor.offsetToLogicalPosition(range.startOffset).column
    if (caret.vimLastColumn == MotionGroup.LAST_COLUMN) {
      col = MotionGroup.LAST_COLUMN
    }
  }

  // Remove the range
  val vimCaret = IjVimCaret(caret)
  val indent = editor.offsetToLogicalPosition(vimEditor.indentForLine(vimCaret.getLine())).column
  val deletedInfo = VimMachine.instance.delete(vimRange, vimEditor, vimCaret)
  if (deletedInfo != null) {
    if (deletedInfo is OperatedRange.Lines) {
      // Add new line in case of linewise motion
      val existingLine = if (vimEditor.fileSize() != 0L) {
        vimEditor.addLine(EditorLine.Exclusive.init(deletedInfo.lineAbove.line, vimEditor))
      } else {
        EditorLine.Inclusive.init(0, vimEditor)
      }

      val offset = vimCaret.offsetForLineWithStartOfLineOption(existingLine)
      // TODO: 29.12.2021 IndentConfig is not abstract
      val indentText = IndentConfig.create(editor).createIndentBySize(indent)
      vimEditor.insertText(offset.excl, indentText)
      val caretOffset = offset + (indentText.length - 1).coerceAtLeast(0)
      vimCaret.moveToOffset(caretOffset)
      VimPlugin.getChange().insertBeforeCursor(editor, context)
    } else {
      when (deletedInfo) {
        is OperatedRange.Characters -> {
          vimCaret.moveToOffset(deletedInfo.leftOffset.point)
        }
        is OperatedRange.Block -> TODO()
      }
      if (type == SelectionType.BLOCK_WISE) {
        VimPlugin.getChange().setInsertRepeat(lines, col, false)
      }
      editor.vimChangeActionSwitchMode = CommandState.Mode.INSERT
    }
  } else {
    VimPlugin.getChange().insertBeforeCursor(editor, context)
  }
}

fun deleteRange(
  editor: Editor,
  caret: Caret,
  range: TextRange,
  type: SelectionType,
): Boolean {
  val vimEditor = IjVimEditor(editor)
  val vimRange = toVimRange(range, type)

  val vimCaret = IjVimCaret(caret)
  vimCaret.caret.vimLastColumn = vimCaret.caret.inlayAwareVisualColumn
  val deletedInfo = VimMachine.instance.delete(vimRange, vimEditor, vimCaret)
  if (deletedInfo != null) {
    when (deletedInfo) {
      is OperatedRange.Characters -> {
        val newOffset = EditorHelper.normalizeOffset(editor, deletedInfo.leftOffset.point, false)
        vimCaret.moveToOffset(newOffset)
      }
      is OperatedRange.Block -> TODO()
      is OperatedRange.Lines -> {
        val line = EditorLine.Inclusive.init(deletedInfo.lineAbove.line.coerceAtMost(vimEditor.lineCount() - 1), vimEditor)
        val offset = vimCaret.offsetForLineWithStartOfLineOption(line)
        vimCaret.moveToOffset(offset)
      }
    }
  }
  return deletedInfo != null
}
