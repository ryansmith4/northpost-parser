# Generated from CanadianAddress.g4 by ANTLR 4.13.2
from antlr4 import *
if "." in __name__:
    from .CanadianAddressParser import CanadianAddressParser
else:
    from CanadianAddressParser import CanadianAddressParser

# This class defines a complete generic visitor for a parse tree produced by CanadianAddressParser.

class CanadianAddressVisitor(ParseTreeVisitor):

    # Visit a parse tree produced by CanadianAddressParser#address.
    def visitAddress(self, ctx:CanadianAddressParser.AddressContext):
        return self.visitChildren(ctx)


    # Visit a parse tree produced by CanadianAddressParser#addressBody.
    def visitAddressBody(self, ctx:CanadianAddressParser.AddressBodyContext):
        return self.visitChildren(ctx)


    # Visit a parse tree produced by CanadianAddressParser#lineSep.
    def visitLineSep(self, ctx:CanadianAddressParser.LineSepContext):
        return self.visitChildren(ctx)


    # Visit a parse tree produced by CanadianAddressParser#addressLine.
    def visitAddressLine(self, ctx:CanadianAddressParser.AddressLineContext):
        return self.visitChildren(ctx)


    # Visit a parse tree produced by CanadianAddressParser#lineContent.
    def visitLineContent(self, ctx:CanadianAddressParser.LineContentContext):
        return self.visitChildren(ctx)


    # Visit a parse tree produced by CanadianAddressParser#lineToken.
    def visitLineToken(self, ctx:CanadianAddressParser.LineTokenContext):
        return self.visitChildren(ctx)



del CanadianAddressParser