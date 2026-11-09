# Generated from CanadianAddress.g4 by ANTLR 4.13.2
# encoding: utf-8
from antlr4 import *
from io import StringIO
import sys
if sys.version_info[1] > 5:
	from typing import TextIO
else:
	from typing.io import TextIO

def serializedATN():
    return [
        4,1,14,78,2,0,7,0,2,1,7,1,2,2,7,2,2,3,7,3,2,4,7,4,2,5,7,5,1,0,5,
        0,14,8,0,10,0,12,0,17,9,0,1,0,1,0,5,0,21,8,0,10,0,12,0,24,9,0,1,
        0,1,0,1,1,1,1,1,1,1,1,5,1,32,8,1,10,1,12,1,35,9,1,1,2,1,2,5,2,39,
        8,2,10,2,12,2,42,9,2,1,2,5,2,45,8,2,10,2,12,2,48,9,2,1,3,5,3,51,
        8,3,10,3,12,3,54,9,3,1,3,1,3,5,3,58,8,3,10,3,12,3,61,9,3,1,4,1,4,
        5,4,65,8,4,10,4,12,4,68,9,4,1,4,5,4,71,8,4,10,4,12,4,74,9,4,1,5,
        1,5,1,5,0,0,6,0,2,4,6,8,10,0,2,1,0,12,13,1,0,1,11,80,0,15,1,0,0,
        0,2,27,1,0,0,0,4,36,1,0,0,0,6,52,1,0,0,0,8,62,1,0,0,0,10,75,1,0,
        0,0,12,14,7,0,0,0,13,12,1,0,0,0,14,17,1,0,0,0,15,13,1,0,0,0,15,16,
        1,0,0,0,16,18,1,0,0,0,17,15,1,0,0,0,18,22,3,2,1,0,19,21,7,0,0,0,
        20,19,1,0,0,0,21,24,1,0,0,0,22,20,1,0,0,0,22,23,1,0,0,0,23,25,1,
        0,0,0,24,22,1,0,0,0,25,26,5,0,0,1,26,1,1,0,0,0,27,33,3,6,3,0,28,
        29,3,4,2,0,29,30,3,6,3,0,30,32,1,0,0,0,31,28,1,0,0,0,32,35,1,0,0,
        0,33,31,1,0,0,0,33,34,1,0,0,0,34,3,1,0,0,0,35,33,1,0,0,0,36,46,5,
        12,0,0,37,39,5,13,0,0,38,37,1,0,0,0,39,42,1,0,0,0,40,38,1,0,0,0,
        40,41,1,0,0,0,41,43,1,0,0,0,42,40,1,0,0,0,43,45,5,12,0,0,44,40,1,
        0,0,0,45,48,1,0,0,0,46,44,1,0,0,0,46,47,1,0,0,0,47,5,1,0,0,0,48,
        46,1,0,0,0,49,51,5,13,0,0,50,49,1,0,0,0,51,54,1,0,0,0,52,50,1,0,
        0,0,52,53,1,0,0,0,53,55,1,0,0,0,54,52,1,0,0,0,55,59,3,8,4,0,56,58,
        5,13,0,0,57,56,1,0,0,0,58,61,1,0,0,0,59,57,1,0,0,0,59,60,1,0,0,0,
        60,7,1,0,0,0,61,59,1,0,0,0,62,72,3,10,5,0,63,65,5,13,0,0,64,63,1,
        0,0,0,65,68,1,0,0,0,66,64,1,0,0,0,66,67,1,0,0,0,67,69,1,0,0,0,68,
        66,1,0,0,0,69,71,3,10,5,0,70,66,1,0,0,0,71,74,1,0,0,0,72,70,1,0,
        0,0,72,73,1,0,0,0,73,9,1,0,0,0,74,72,1,0,0,0,75,76,7,1,0,0,76,11,
        1,0,0,0,9,15,22,33,40,46,52,59,66,72
    ]

class CanadianAddressParser ( Parser ):

    grammarFileName = "CanadianAddress.g4"

    atn = ATNDeserializer().deserialize(serializedATN())

    decisionsToDFA = [ DFA(ds, i) for i, ds in enumerate(atn.decisionToState) ]

    sharedContextCache = PredictionContextCache()

    literalNames = [ "<INVALID>", "<INVALID>", "<INVALID>", "<INVALID>", 
                     "<INVALID>", "'/'", "'-'", "'.'", "','", "'&'", "'('", 
                     "')'" ]

    symbolicNames = [ "<INVALID>", "POSTAL_CODE", "NUMBER", "ALPHANUMERIC", 
                      "WORD", "SLASH", "HYPHEN", "DOT", "COMMA", "AMPERSAND", 
                      "LPAREN", "RPAREN", "NL", "WS", "CATCHALL" ]

    RULE_address = 0
    RULE_addressBody = 1
    RULE_lineSep = 2
    RULE_addressLine = 3
    RULE_lineContent = 4
    RULE_lineToken = 5

    ruleNames =  [ "address", "addressBody", "lineSep", "addressLine", "lineContent", 
                   "lineToken" ]

    EOF = Token.EOF
    POSTAL_CODE=1
    NUMBER=2
    ALPHANUMERIC=3
    WORD=4
    SLASH=5
    HYPHEN=6
    DOT=7
    COMMA=8
    AMPERSAND=9
    LPAREN=10
    RPAREN=11
    NL=12
    WS=13
    CATCHALL=14

    def __init__(self, input:TokenStream, output:TextIO = sys.stdout):
        super().__init__(input, output)
        self.checkVersion("4.13.2")
        self._interp = ParserATNSimulator(self, self.atn, self.decisionsToDFA, self.sharedContextCache)
        self._predicates = None




    class AddressContext(ParserRuleContext):
        __slots__ = 'parser'

        def __init__(self, parser, parent:ParserRuleContext=None, invokingState:int=-1):
            super().__init__(parent, invokingState)
            self.parser = parser

        def addressBody(self):
            return self.getTypedRuleContext(CanadianAddressParser.AddressBodyContext,0)


        def EOF(self):
            return self.getToken(CanadianAddressParser.EOF, 0)

        def WS(self, i:int=None):
            if i is None:
                return self.getTokens(CanadianAddressParser.WS)
            else:
                return self.getToken(CanadianAddressParser.WS, i)

        def NL(self, i:int=None):
            if i is None:
                return self.getTokens(CanadianAddressParser.NL)
            else:
                return self.getToken(CanadianAddressParser.NL, i)

        def getRuleIndex(self):
            return CanadianAddressParser.RULE_address

        def accept(self, visitor:ParseTreeVisitor):
            if hasattr( visitor, "visitAddress" ):
                return visitor.visitAddress(self)
            else:
                return visitor.visitChildren(self)




    def address(self):

        localctx = CanadianAddressParser.AddressContext(self, self._ctx, self.state)
        self.enterRule(localctx, 0, self.RULE_address)
        self._la = 0 # Token type
        try:
            self.enterOuterAlt(localctx, 1)
            self.state = 15
            self._errHandler.sync(self)
            _alt = self._interp.adaptivePredict(self._input,0,self._ctx)
            while _alt!=2 and _alt!=ATN.INVALID_ALT_NUMBER:
                if _alt==1:
                    self.state = 12
                    _la = self._input.LA(1)
                    if not(_la==12 or _la==13):
                        self._errHandler.recoverInline(self)
                    else:
                        self._errHandler.reportMatch(self)
                        self.consume() 
                self.state = 17
                self._errHandler.sync(self)
                _alt = self._interp.adaptivePredict(self._input,0,self._ctx)

            self.state = 18
            self.addressBody()
            self.state = 22
            self._errHandler.sync(self)
            _la = self._input.LA(1)
            while _la==12 or _la==13:
                self.state = 19
                _la = self._input.LA(1)
                if not(_la==12 or _la==13):
                    self._errHandler.recoverInline(self)
                else:
                    self._errHandler.reportMatch(self)
                    self.consume()
                self.state = 24
                self._errHandler.sync(self)
                _la = self._input.LA(1)

            self.state = 25
            self.match(CanadianAddressParser.EOF)
        except RecognitionException as re:
            localctx.exception = re
            self._errHandler.reportError(self, re)
            self._errHandler.recover(self, re)
        finally:
            self.exitRule()
        return localctx


    class AddressBodyContext(ParserRuleContext):
        __slots__ = 'parser'

        def __init__(self, parser, parent:ParserRuleContext=None, invokingState:int=-1):
            super().__init__(parent, invokingState)
            self.parser = parser

        def addressLine(self, i:int=None):
            if i is None:
                return self.getTypedRuleContexts(CanadianAddressParser.AddressLineContext)
            else:
                return self.getTypedRuleContext(CanadianAddressParser.AddressLineContext,i)


        def lineSep(self, i:int=None):
            if i is None:
                return self.getTypedRuleContexts(CanadianAddressParser.LineSepContext)
            else:
                return self.getTypedRuleContext(CanadianAddressParser.LineSepContext,i)


        def getRuleIndex(self):
            return CanadianAddressParser.RULE_addressBody

        def accept(self, visitor:ParseTreeVisitor):
            if hasattr( visitor, "visitAddressBody" ):
                return visitor.visitAddressBody(self)
            else:
                return visitor.visitChildren(self)




    def addressBody(self):

        localctx = CanadianAddressParser.AddressBodyContext(self, self._ctx, self.state)
        self.enterRule(localctx, 2, self.RULE_addressBody)
        try:
            self.enterOuterAlt(localctx, 1)
            self.state = 27
            self.addressLine()
            self.state = 33
            self._errHandler.sync(self)
            _alt = self._interp.adaptivePredict(self._input,2,self._ctx)
            while _alt!=2 and _alt!=ATN.INVALID_ALT_NUMBER:
                if _alt==1:
                    self.state = 28
                    self.lineSep()
                    self.state = 29
                    self.addressLine() 
                self.state = 35
                self._errHandler.sync(self)
                _alt = self._interp.adaptivePredict(self._input,2,self._ctx)

        except RecognitionException as re:
            localctx.exception = re
            self._errHandler.reportError(self, re)
            self._errHandler.recover(self, re)
        finally:
            self.exitRule()
        return localctx


    class LineSepContext(ParserRuleContext):
        __slots__ = 'parser'

        def __init__(self, parser, parent:ParserRuleContext=None, invokingState:int=-1):
            super().__init__(parent, invokingState)
            self.parser = parser

        def NL(self, i:int=None):
            if i is None:
                return self.getTokens(CanadianAddressParser.NL)
            else:
                return self.getToken(CanadianAddressParser.NL, i)

        def WS(self, i:int=None):
            if i is None:
                return self.getTokens(CanadianAddressParser.WS)
            else:
                return self.getToken(CanadianAddressParser.WS, i)

        def getRuleIndex(self):
            return CanadianAddressParser.RULE_lineSep

        def accept(self, visitor:ParseTreeVisitor):
            if hasattr( visitor, "visitLineSep" ):
                return visitor.visitLineSep(self)
            else:
                return visitor.visitChildren(self)




    def lineSep(self):

        localctx = CanadianAddressParser.LineSepContext(self, self._ctx, self.state)
        self.enterRule(localctx, 4, self.RULE_lineSep)
        self._la = 0 # Token type
        try:
            self.enterOuterAlt(localctx, 1)
            self.state = 36
            self.match(CanadianAddressParser.NL)
            self.state = 46
            self._errHandler.sync(self)
            _alt = self._interp.adaptivePredict(self._input,4,self._ctx)
            while _alt!=2 and _alt!=ATN.INVALID_ALT_NUMBER:
                if _alt==1:
                    self.state = 40
                    self._errHandler.sync(self)
                    _la = self._input.LA(1)
                    while _la==13:
                        self.state = 37
                        self.match(CanadianAddressParser.WS)
                        self.state = 42
                        self._errHandler.sync(self)
                        _la = self._input.LA(1)

                    self.state = 43
                    self.match(CanadianAddressParser.NL) 
                self.state = 48
                self._errHandler.sync(self)
                _alt = self._interp.adaptivePredict(self._input,4,self._ctx)

        except RecognitionException as re:
            localctx.exception = re
            self._errHandler.reportError(self, re)
            self._errHandler.recover(self, re)
        finally:
            self.exitRule()
        return localctx


    class AddressLineContext(ParserRuleContext):
        __slots__ = 'parser'

        def __init__(self, parser, parent:ParserRuleContext=None, invokingState:int=-1):
            super().__init__(parent, invokingState)
            self.parser = parser

        def lineContent(self):
            return self.getTypedRuleContext(CanadianAddressParser.LineContentContext,0)


        def WS(self, i:int=None):
            if i is None:
                return self.getTokens(CanadianAddressParser.WS)
            else:
                return self.getToken(CanadianAddressParser.WS, i)

        def getRuleIndex(self):
            return CanadianAddressParser.RULE_addressLine

        def accept(self, visitor:ParseTreeVisitor):
            if hasattr( visitor, "visitAddressLine" ):
                return visitor.visitAddressLine(self)
            else:
                return visitor.visitChildren(self)




    def addressLine(self):

        localctx = CanadianAddressParser.AddressLineContext(self, self._ctx, self.state)
        self.enterRule(localctx, 6, self.RULE_addressLine)
        self._la = 0 # Token type
        try:
            self.enterOuterAlt(localctx, 1)
            self.state = 52
            self._errHandler.sync(self)
            _la = self._input.LA(1)
            while _la==13:
                self.state = 49
                self.match(CanadianAddressParser.WS)
                self.state = 54
                self._errHandler.sync(self)
                _la = self._input.LA(1)

            self.state = 55
            self.lineContent()
            self.state = 59
            self._errHandler.sync(self)
            _alt = self._interp.adaptivePredict(self._input,6,self._ctx)
            while _alt!=2 and _alt!=ATN.INVALID_ALT_NUMBER:
                if _alt==1:
                    self.state = 56
                    self.match(CanadianAddressParser.WS) 
                self.state = 61
                self._errHandler.sync(self)
                _alt = self._interp.adaptivePredict(self._input,6,self._ctx)

        except RecognitionException as re:
            localctx.exception = re
            self._errHandler.reportError(self, re)
            self._errHandler.recover(self, re)
        finally:
            self.exitRule()
        return localctx


    class LineContentContext(ParserRuleContext):
        __slots__ = 'parser'

        def __init__(self, parser, parent:ParserRuleContext=None, invokingState:int=-1):
            super().__init__(parent, invokingState)
            self.parser = parser

        def lineToken(self, i:int=None):
            if i is None:
                return self.getTypedRuleContexts(CanadianAddressParser.LineTokenContext)
            else:
                return self.getTypedRuleContext(CanadianAddressParser.LineTokenContext,i)


        def WS(self, i:int=None):
            if i is None:
                return self.getTokens(CanadianAddressParser.WS)
            else:
                return self.getToken(CanadianAddressParser.WS, i)

        def getRuleIndex(self):
            return CanadianAddressParser.RULE_lineContent

        def accept(self, visitor:ParseTreeVisitor):
            if hasattr( visitor, "visitLineContent" ):
                return visitor.visitLineContent(self)
            else:
                return visitor.visitChildren(self)




    def lineContent(self):

        localctx = CanadianAddressParser.LineContentContext(self, self._ctx, self.state)
        self.enterRule(localctx, 8, self.RULE_lineContent)
        self._la = 0 # Token type
        try:
            self.enterOuterAlt(localctx, 1)
            self.state = 62
            self.lineToken()
            self.state = 72
            self._errHandler.sync(self)
            _alt = self._interp.adaptivePredict(self._input,8,self._ctx)
            while _alt!=2 and _alt!=ATN.INVALID_ALT_NUMBER:
                if _alt==1:
                    self.state = 66
                    self._errHandler.sync(self)
                    _la = self._input.LA(1)
                    while _la==13:
                        self.state = 63
                        self.match(CanadianAddressParser.WS)
                        self.state = 68
                        self._errHandler.sync(self)
                        _la = self._input.LA(1)

                    self.state = 69
                    self.lineToken() 
                self.state = 74
                self._errHandler.sync(self)
                _alt = self._interp.adaptivePredict(self._input,8,self._ctx)

        except RecognitionException as re:
            localctx.exception = re
            self._errHandler.reportError(self, re)
            self._errHandler.recover(self, re)
        finally:
            self.exitRule()
        return localctx


    class LineTokenContext(ParserRuleContext):
        __slots__ = 'parser'

        def __init__(self, parser, parent:ParserRuleContext=None, invokingState:int=-1):
            super().__init__(parent, invokingState)
            self.parser = parser

        def WORD(self):
            return self.getToken(CanadianAddressParser.WORD, 0)

        def NUMBER(self):
            return self.getToken(CanadianAddressParser.NUMBER, 0)

        def POSTAL_CODE(self):
            return self.getToken(CanadianAddressParser.POSTAL_CODE, 0)

        def ALPHANUMERIC(self):
            return self.getToken(CanadianAddressParser.ALPHANUMERIC, 0)

        def HYPHEN(self):
            return self.getToken(CanadianAddressParser.HYPHEN, 0)

        def SLASH(self):
            return self.getToken(CanadianAddressParser.SLASH, 0)

        def DOT(self):
            return self.getToken(CanadianAddressParser.DOT, 0)

        def COMMA(self):
            return self.getToken(CanadianAddressParser.COMMA, 0)

        def AMPERSAND(self):
            return self.getToken(CanadianAddressParser.AMPERSAND, 0)

        def LPAREN(self):
            return self.getToken(CanadianAddressParser.LPAREN, 0)

        def RPAREN(self):
            return self.getToken(CanadianAddressParser.RPAREN, 0)

        def getRuleIndex(self):
            return CanadianAddressParser.RULE_lineToken

        def accept(self, visitor:ParseTreeVisitor):
            if hasattr( visitor, "visitLineToken" ):
                return visitor.visitLineToken(self)
            else:
                return visitor.visitChildren(self)




    def lineToken(self):

        localctx = CanadianAddressParser.LineTokenContext(self, self._ctx, self.state)
        self.enterRule(localctx, 10, self.RULE_lineToken)
        self._la = 0 # Token type
        try:
            self.enterOuterAlt(localctx, 1)
            self.state = 75
            _la = self._input.LA(1)
            if not((((_la) & ~0x3f) == 0 and ((1 << _la) & 4094) != 0)):
                self._errHandler.recoverInline(self)
            else:
                self._errHandler.reportMatch(self)
                self.consume()
        except RecognitionException as re:
            localctx.exception = re
            self._errHandler.reportError(self, re)
            self._errHandler.recover(self, re)
        finally:
            self.exitRule()
        return localctx





