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

package com.maddyhome.idea.vim.vimscript.model.statements.loops

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.maddyhome.idea.vim.vimscript.model.Executable
import com.maddyhome.idea.vim.vimscript.model.ExecutionResult
import com.maddyhome.idea.vim.vimscript.model.expressions.Expression

data class WhileLoop(val condition: Expression, val body: List<Executable>) : Executable {
  override lateinit var parent: Executable

  override fun execute(editor: Editor, context: DataContext): ExecutionResult {
    var result: ExecutionResult = ExecutionResult.Success
    body.forEach { it.parent = this }

    while (condition.evaluate(editor, context, this).asBoolean()) {
      for (statement in body) {
        if (result is ExecutionResult.Success) {
          result = statement.execute(editor, context)
        } else {
          break
        }
      }
      if (result is ExecutionResult.Break) {
        result = ExecutionResult.Success
        break
      } else if (result is ExecutionResult.Continue) {
        result = ExecutionResult.Success
        continue
      } else if (result is ExecutionResult.Error) {
        break
      }
    }
    return result
  }
}
